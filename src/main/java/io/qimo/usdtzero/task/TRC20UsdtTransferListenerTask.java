package io.qimo.usdtzero.task;

import io.qimo.usdtzero.config.ChainProperties;
import io.qimo.usdtzero.config.PayProperties;
import io.qimo.usdtzero.constant.ChainType;
import io.qimo.usdtzero.service.AmountPoolService;
import io.qimo.usdtzero.service.LightweightMetricsService;
import io.qimo.usdtzero.service.OrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.tron.trident.core.ApiWrapper;
import org.tron.trident.core.NodeType;
import org.tron.trident.proto.Response.TransactionInfo;
import org.tron.trident.proto.Response.TransactionExtention;
import org.tron.trident.proto.Response.BlockExtention;
import org.tron.trident.proto.Response.BlockListExtention;
import org.tron.trident.proto.Response.TransactionInfoList;
import org.tron.trident.proto.Contract.TriggerSmartContract;
import org.tron.trident.proto.Chain.Transaction;
import org.tron.trident.core.exceptions.IllegalException;
import org.tron.trident.core.utils.ByteArray;
import com.google.protobuf.InvalidProtocolBufferException;

import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.tron.trident.utils.Base58Check;

/**
 * TRC20 USDT转账监听任务（区块轮询+交易解析，配置与状态全部依赖业务Bean）
 */
@Slf4j
@Component
@ConditionalOnProperty(value = "chain.trc20-enable", havingValue = "true")
public class TRC20UsdtTransferListenerTask {
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

    private ApiWrapper tronClient;
    private NodeType nodeType;
    private long lastScannedBlock = -1;

    private Thread watcherThread;
    private final long checkInterval = 3000L; // 3秒检查一次

    // Transfer event topic
    private static final String TRANSFER_EVENT_TOPIC = "ddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef";

    @PostConstruct
    public void init() throws IllegalException, InvalidProtocolBufferException {
        String baseRpc = chainProperties.getTrc20Rpc();
        String fullNodeRpc = baseRpc + ":50051";
        String solidityNodeRpc = baseRpc + ":50052";
        // 根据配置决定使用全节点还是固化节点
        nodeType = payProperties.getTradeIsConfirmed() ? NodeType.SOLIDITY_NODE : NodeType.FULL_NODE;
        tronClient = new ApiWrapper(fullNodeRpc, solidityNodeRpc, "");
        log.info("TRC20 监听任务启动，使用节点类型：{}，当前监听地址数：{}", 
            nodeType, amountPoolService.getAllLockedAmounts().size());
    }

    @EventListener(ApplicationReadyEvent.class)
    public void startWatching() {
        watcherThread = new Thread(this::run, "trc20-blockchain-watcher");
        watcherThread.setDaemon(true);
        watcherThread.start();
        log.info("TRC20 监听任务开始轮询区块...");
    }

    public void run() {
        long nextCheck = 0;
        
        while (!Thread.interrupted()) {
            if (nextCheck <= System.currentTimeMillis()) {
                try {
                    nextCheck = System.currentTimeMillis() + checkInterval;
                    pollTronBlocks();
                } catch (Exception ex) {
                    log.error("TRC20 监听任务异常", ex);
                }
            } else {
                try {
                    Thread.sleep(Math.max(nextCheck - System.currentTimeMillis(), 100));
                } catch (InterruptedException ex) {
                    log.info("TRC20 监听任务被中断");
                    break;
                }
            }
        }
    }

    @PreDestroy
    public void stopWatching() {
        log.info("TRC20 监听任务正在关闭...");
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
     * 每3秒轮询一次波场区块，解析USDT转账
     */
    public void pollTronBlocks() {
        Timer.Sample timer = metricsService.startScheduledTaskTimer();
        try {
            BlockExtention latestBlock = tronClient.getNowBlock2(nodeType);
            int maxBacklog = 5;
            long startBlock = Math.max(lastScannedBlock + 1, latestBlock.getBlockHeader().getRawData().getNumber() - maxBacklog + 1);
            if (startBlock > latestBlock.getBlockHeader().getRawData().getNumber()) {
                return;
            }

            // 批量获取区块
            BlockListExtention blocks = tronClient.getBlockByLimitNext(startBlock, latestBlock.getBlockHeader().getRawData().getNumber() + 1);
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            
            for (BlockExtention block : blocks.getBlockList()) {
                long blockNum = block.getBlockHeader().getRawData().getNumber();
                if (lastScannedBlock >= blockNum) continue;
                if (lastScannedBlock < blockNum) {
                    lastScannedBlock = blockNum;
                }
                futures.add(parseBlock(blockNum));
            }
            if (!futures.isEmpty()) {
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            }
        } catch (Exception e) {
            log.error("TRC20 监听任务异常", e);
            metricsService.recordScheduledTaskError("trc20_block_scan", e.getMessage());
        } finally {
            metricsService.stopScheduledTaskTimer(timer, "trc20_block_scan");
        }
    }

    /**
     * 解析指定区块内的USDT转账
     */
    public CompletableFuture<Void> parseBlock(long blockNum) {
        Set<String> listenAmount = amountPoolService.getAllLockedAmounts();
        Timer.Sample timer = metricsService.startScheduledTaskTimer();
        return CompletableFuture.runAsync(() -> {
                TransactionInfoList txInfoList = null;
                try {
                    txInfoList = tronClient.getTransactionInfoByBlockNum(blockNum);
                    metricsService.incBlockScanSuccess(ChainType.TRC20);
                } catch (IllegalException e) {
                    metricsService.incBlockScanFail(ChainType.TRC20);
                    log.error("TRC20 解析区块{}时发生异常", blockNum, e);
                    return;
                }
                for (TransactionInfo txInfo : txInfoList.getTransactionInfoList()) {
                    String txId = ByteArray.toHexString(txInfo.getId().toByteArray());
                    if (txInfo.getReceipt().getResult() != Transaction.Result.contractResult.SUCCESS) {
                        continue;
                    }
                    String contractHex = ByteArray.toHexString(txInfo.getContractAddress().toByteArray());
                    String contractBase58 = Base58Check.bytesToBase58(ByteArray.fromHexString(contractHex));
                    if (!contractBase58.equals(chainProperties.getTrc20SmartContract())) {
                        continue;
                    }
                    for (TransactionInfo.Log logItem : txInfo.getLogList()) {
                        String topic0Hex = ByteArray.toHexString(logItem.getTopics(0).toByteArray());
                        if (logItem.getTopicsCount() == 3 && topic0Hex.equals(TRANSFER_EVENT_TOPIC)) {
                            byte[] topic1 = logItem.getTopics(1).toByteArray();
                            byte[] topic2 = logItem.getTopics(2).toByteArray();
                            byte[] addr1 = new byte[21];
                            addr1[0] = 0x41;
                            System.arraycopy(topic1, 12, addr1, 1, 20);
                            byte[] addr2 = new byte[21];
                            addr2[0] = 0x41;
                            System.arraycopy(topic2, 12, addr2, 1, 20);
                            String from = Base58Check.bytesToBase58(addr1);
                            String to = Base58Check.bytesToBase58(addr2);
                            BigInteger amount = new BigInteger(logItem.getData().toByteArray());
                            if (listenAmount.contains(amountPoolService.buildKey(to, amount.longValue()))) {
                                log.info("TRC20 转账: block={}, from={}, to={}, amount={}, txId={}", blockNum, from, to, amount, txId);
                                orderService.markOrderAsPaid(to, amount.longValue(), txId);
                            }
                        }
                    }
                }

        }).exceptionally(e -> {
            log.warn("解析block={}区块失败: {}", blockNum, e.getMessage());
            metricsService.recordScheduledTaskError("trc20_block_parse", e.getMessage());
            return null;
        }).whenComplete((result, ex) -> {
            metricsService.stopScheduledTaskTimer(timer, "trc20_block_parse");
        });
    }
} 