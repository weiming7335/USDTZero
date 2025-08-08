package io.qimo.usdtzero.task;

import io.qimo.usdtzero.config.ChainProperties;
import io.qimo.usdtzero.config.PayProperties;
import io.qimo.usdtzero.constant.ChainType;
import io.qimo.usdtzero.service.AmountPoolService;
import io.qimo.usdtzero.service.OrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.meta.AccountMeta;
import software.sava.core.tx.Instruction;
import software.sava.core.tx.Transaction;
import software.sava.core.tx.TransactionSkeleton;
import software.sava.rpc.json.http.client.SolanaRpcClient;
import software.sava.rpc.json.http.request.BlockTxDetails;
import software.sava.rpc.json.http.request.Commitment;
import software.sava.rpc.json.http.response.BlockTx;

import java.net.URI;
import java.net.http.HttpClient;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import io.qimo.usdtzero.service.LightweightMetricsService;
import io.micrometer.core.instrument.Timer;


/**
 * Solana USDT转账监听任务（区块轮询+交易解析，配置与状态全部依赖业务Bean）
 */
@Slf4j
@Component
@ConditionalOnProperty(value = "chain.spl-enable", havingValue = "true")
public class SolanaUsdtTransferListenerTask {
    @Autowired
    private ChainProperties chainProperties;
    @Autowired
    private PayProperties payProperties;
    @Autowired
    private AmountPoolService amountPoolService;
    @Autowired
    private OrderService orderService;
    @Autowired
    private LightweightMetricsService metricsService;

    private SolanaRpcClient rpcClient;
    private long lastScannedSlot = -1;
    private Commitment commitment;

    private Thread watcherThread;
    private final long checkInterval = 1000L; // 3秒检查一次

    private static final String TOKEN_PROGRAM_ID = "TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA";

    @PostConstruct
    public void init() {
        rpcClient = SolanaRpcClient.createClient(URI.create(chainProperties.getSplRpc()), HttpClient.newHttpClient());
        this.commitment = payProperties.getTradeIsConfirmed()
            ? Commitment.FINALIZED
            : Commitment.CONFIRMED;
        log.info("[SPL] Solana 监听任务启动，当前监听地址数：{}", amountPoolService.getAllLockedAmounts().size());
    }

    @EventListener(ApplicationReadyEvent.class)
    public void startWatching() {
        watcherThread = new Thread(this::run, "solana-blockchain-watcher");
        watcherThread.setDaemon(true);
        watcherThread.start();
        log.info("Solana 监听任务开始轮询区块...");
    }

    public void run() {
        long nextCheck = 0;
        
        while (!Thread.interrupted()) {
            if (nextCheck <= System.currentTimeMillis()) {
                try {
                    nextCheck = System.currentTimeMillis() + checkInterval;
                    pollSolanaBlocks();
                } catch (Exception ex) {
                    log.error("Solana 监听任务异常", ex);
                }
            } else {
                try {
                    Thread.sleep(Math.max(nextCheck - System.currentTimeMillis(), 100));
                } catch (InterruptedException ex) {
                    log.info("Solana 监听任务被中断");
                    break;
                }
            }
        }
    }

    @PreDestroy
    public void stopWatching() {
        log.info("Solana 监听任务正在关闭...");
        if (watcherThread != null) {
            watcherThread.interrupt();
            try {
                watcherThread.join(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * 每3秒轮询一次Solana区块，解析USDT转账
     */
    public void pollSolanaBlocks() {
        Timer.Sample timer = metricsService.startScheduledTaskTimer();
        try {
            long endSlot = rpcClient.getSlot(commitment).join();
            int maxBacklog = 10;
            long startSlot = Math.max(lastScannedSlot + 1, endSlot - maxBacklog + 1);
            if (startSlot > endSlot) {
                return;
            }
            long[] slots = rpcClient.getBlocks(startSlot, endSlot).join();
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            
            // 打印recentScannedSlots中最大的区块和当前slots信息
            log.info("[SPL] 当前状态: lastScannedSlot={}, 本次扫描slots={}", 
                lastScannedSlot, Arrays.toString(slots));
            
            for (long slot : slots) {
                if (slot > lastScannedSlot) {
                    lastScannedSlot = slot;
                }
                futures.add(parseBlockForUsdtTransfers(slot));
            }
            if (!futures.isEmpty()) {
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            }
        } catch (Exception e) {
            log.error("[SPL] Solana 监听任务异常", e);
            metricsService.recordScheduledTaskError("solana_block_scan", e.getMessage());
        } finally {
            metricsService.stopScheduledTaskTimer(timer, "solana_block_scan");
        }
    }

    /**
     * 解析指定slot区块内的USDT转账
     */
    public CompletableFuture<Void> parseBlockForUsdtTransfers(long slot) {
        Set<String> listenAmount = amountPoolService.getAllLockedAmounts();

        return rpcClient.getBlock(slot, BlockTxDetails.full, 0)
            .thenAccept(block -> {
                metricsService.incBlockScanSuccess(ChainType.SPL);
                try {
                    if (block == null) return;
                    List<BlockTx> transactions = block.transactions();

                    log.info("[SPL] 区块 {} 共 {} 个交易", slot, transactions.size());
                    for (BlockTx tx : transactions) {
                        String txid = Transaction.getBase58Id(tx.data());
                        if (tx.meta() != null && tx.meta().error() != null) {
                            log.info("交易失败: txid={}, 错误信息={}", txid, tx.meta().error());
                            continue;
                        }
                        byte[] data = tx.data();
                        if (data == null || data.length == 0) {
                            log.debug("[SPL] 交易数据为空，跳过: txid={}", txid);
                            continue;
                        }
                        

                        TransactionSkeleton skeleton = TransactionSkeleton.deserializeSkeleton(data);
                        Instruction[] instructions = skeleton.parseLegacyInstructions();
                        if (instructions != null) {
                        for (Instruction ix : instructions) {
                            if (ix.programId() == null) {
                                log.debug("[SPL] 指令程序ID为空，跳过");
                                continue;
                            }

                            String programId = ix.programId().publicKey().toBase58();
                            if (programId.equals(TOKEN_PROGRAM_ID)) {
                                byte[] ixData = ix.data();
                                int offset = ix.offset();
                                int len = ix.len();

                                // 验证数据完整性
                                if (ixData == null || len <= 0 || offset < 0 ||
                                    offset + len > ixData.length || offset + 9 > ixData.length) {
                                    log.debug("[SPL] 指令数据不完整，跳过: offset={}, len={}, dataLength={}",
                                            offset, len, ixData != null ? ixData.length : 0);
                                    continue;
                                }
                                    int instructionType = ixData[offset] & 0xFF;
                                    // 只处理 TransferChecked 指令
                                    if (instructionType == 12) {  // TransferChecked
                                        List<AccountMeta> ixAccounts = ix.accounts();
                                        if (ixAccounts.size() > 2) {
                                            // 添加空值检查
                                            AccountMeta mintAccount = ixAccounts.get(1);
                                            AccountMeta fromAccount = ixAccounts.get(0);
                                            AccountMeta toAccount = ixAccounts.get(2);

                                            if (mintAccount == null || fromAccount == null || toAccount == null) {
                                                log.debug("[SPL] 账户信息不完整，跳过交易: txid={}", txid);
                                                continue;
                                            }

                                            String mint = mintAccount.publicKey().toBase58();
                                            if (mint.equals(chainProperties.getSplSmartContract())) {
                                                String from = fromAccount.publicKey().toBase58();
                                                String to = toAccount.publicKey().toBase58();
                                                // 解析金额（在指令类型后的8字节）
                                                long amount = 0;
                                                for (int j = 0; j < 8; j++) {
                                                    amount |= ((long) (ixData[offset + 1 + j] & 0xFF) << (j * 8));
                                                }
                                                if (listenAmount.contains(amountPoolService.buildKey(to, amount))) {
                                                    log.info("[SPL] USDT转账: block={}, from={}, to={}, amount={}, txId={}",
                                                            slot, from, to, amount, txid);
                                                    orderService.markOrderAsPaid(to, amount, txid);
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                        }
                    }
                } catch (Exception e) {
                    log.error("[SPL] 解析区块{}时发生异常", slot, e);
                    metricsService.recordScheduledTaskError("solana_block_parse", e.getMessage());
                }
            })
            .exceptionally(e -> {
                log.warn("[SPL] 获取slot={}区块失败: {}", slot, e.getMessage());
                metricsService.incBlockScanFail(ChainType.SPL);
                return null;
            });
    }


}