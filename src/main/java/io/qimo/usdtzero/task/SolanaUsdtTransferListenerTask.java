package io.qimo.usdtzero.task;

import io.qimo.usdtzero.config.ChainProperties;
import io.qimo.usdtzero.config.PayProperties;
import io.qimo.usdtzero.constant.ChainType;
import io.qimo.usdtzero.service.AmountPoolService;
import io.qimo.usdtzero.service.OrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import software.sava.core.accounts.meta.AccountMeta;
import software.sava.core.tx.Instruction;
import software.sava.core.tx.TransactionSkeleton;
import software.sava.rpc.json.http.client.SolanaRpcClient;
import software.sava.rpc.json.http.request.BlockTxDetails;
import software.sava.rpc.json.http.request.Commitment;
import software.sava.rpc.json.http.response.BlockTx;

import java.net.URI;
import java.net.http.HttpClient;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentSkipListSet;
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
    // 使用线程安全的 TreeSet 保存最近扫过的 slot
    private static final int MAX_RECENT_SLOTS = 64; // 最近扫描的区块数量限制
    private final ConcurrentSkipListSet<Long> recentScannedSlots = new ConcurrentSkipListSet<>();
    private long lastScannedSlot = -1;
    private Commitment commitment;
    @PostConstruct
    public void init() {
        rpcClient = SolanaRpcClient.createClient(URI.create(chainProperties.getSplRpc()), HttpClient.newHttpClient());
        this.commitment = payProperties.getTradeIsConfirmed()
            ? Commitment.FINALIZED
            : Commitment.CONFIRMED;
        log.info("[SPL] Solana 监听任务启动，当前监听地址数：{}", amountPoolService.getAllLockedAmounts().size());
    }

    /**
     * 每1.2秒轮询一次Solana区块，解析USDT转账
     */
    @Scheduled(fixedRate = 1000)
    public void pollSolanaBlocks() {
        Timer.Sample timer = metricsService.startScheduledTaskTimer();
        try {
            Set<String> listenAmount = amountPoolService.getAllLockedAmounts();
            if (listenAmount.isEmpty()) {
                log.debug("[SPL] 无监听地址，跳过本轮轮询");
                return;
            }

            long endSlot = rpcClient.getSlot(commitment).join();
            int maxBacklog = 5;
            long startSlot = Math.max(lastScannedSlot + 1, endSlot - maxBacklog + 1);
            if (startSlot > endSlot) {
                return;
            }
            long[] slots = rpcClient.getBlocks(startSlot, endSlot).join();
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            
            // 打印recentScannedSlots中最大的区块和当前slots信息
            Long maxRecentSlot = recentScannedSlots.isEmpty() ? null : recentScannedSlots.last();
            log.info("[SPL] 当前状态: recentScannedSlots最大区块={}, 本次扫描slots={}", 
                maxRecentSlot, Arrays.toString(slots));
            
            for (long slot : slots) {
                if (recentScannedSlots.contains(slot)) continue;
                if (recentScannedSlots.add(slot)) {
                    futures.add(parseBlockForUsdtTransfers(slot));
                    if (recentScannedSlots.size() > MAX_RECENT_SLOTS) {
                        recentScannedSlots.pollFirst();
                    }
                }
                lastScannedSlot = slot;
            }

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
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
        Timer.Sample timer = metricsService.startScheduledTaskTimer();

        return rpcClient.getBlock(slot, BlockTxDetails.signatures, 0)
            .thenAccept(block -> {
                metricsService.incBlockScanSuccess(ChainType.SPL);
                try {
                    if (block == null) return;
                    List<BlockTx> transactions = block.transactions();
                    List<String> sigs = block.signatures();
                    if (transactions == null || transactions.isEmpty() || sigs == null || sigs.size() != transactions.size()) {
                        log.warn("[SPL] 区块{}交易数与签名数不一致，跳过处理: txs={}, sigs={}", slot,
                            transactions == null ? 0 : transactions.size(),
                            sigs == null ? 0 : sigs.size());
                        return;
                    }
                    log.info("[SPL] 区块 {} 共 {} 个交易", slot, transactions.size());
                    for (int i = 0; i < transactions.size(); i++) {
                        BlockTx tx = transactions.get(i);

                        String txid = sigs.get(i);
                        if (tx.meta() != null && tx.meta().error() != null) {
                            log.info("交易失败: txid={}, 错误信息={}", txid, tx.meta().error());
                            continue;
                        }
                        byte[] data = tx.data();
                        TransactionSkeleton skeleton = TransactionSkeleton.deserializeSkeleton(data);
                        Instruction[] instructions = skeleton.parseLegacyInstructions();
                        if (instructions != null) {
                            for (Instruction ix : instructions) {
                                String programId = ix.programId().publicKey().toBase58();
                                if (programId.equals("TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA")) {
                                    byte[] ixData = ix.data();
                                    int offset = ix.offset();
                                    int len = ix.len();
                                    if (len > 0) {
                                        int instructionType = ixData[offset] & 0xFF;
                                        // 只处理 TransferChecked 指令
                                        if (instructionType == 12) {  // TransferChecked
                                            List<AccountMeta> ixAccounts = ix.accounts();
                                            if (ixAccounts.size() > 2) {
                                                String mint = ixAccounts.get(1).publicKey().toBase58();
                                                if (mint.equals(chainProperties.getSplSmartContract())) {
                                                    String from = ixAccounts.get(0).publicKey().toBase58();
                                                    String to = ixAccounts.get(2).publicKey().toBase58();
                                                    // 解析金额（在指令类型后的8字节）
                                                    long amount = 0;
                                                    for (int j = 0; j < 8; j++) {
                                                        amount |= ((long)(ixData[offset + 1 + j] & 0xFF) << (j * 8));
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
            })
            .whenComplete((result, ex) -> {
                metricsService.stopScheduledTaskTimer(timer, "solana_block_parse");
            });
    }
} 