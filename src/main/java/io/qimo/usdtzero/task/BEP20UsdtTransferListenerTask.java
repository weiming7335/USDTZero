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
import org.web3j.crypto.Keys;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.*;
import org.web3j.protocol.http.HttpService;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Type;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import org.web3j.protocol.core.methods.request.EthFilter;

import org.apache.commons.lang3.StringUtils;

@Slf4j
@Component
@ConditionalOnProperty(value = "chain.bep20-enable", havingValue = "true")
public class BEP20UsdtTransferListenerTask {
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

    private Web3j web3j;
    private long lastScannedBlock = -1L;

    private Thread watcherThread;
    private final long checkInterval = 3000L; // 3秒检查一次



    @PostConstruct
    public void init() {
        String bscRpc = chainProperties.getBep20Rpc();
        web3j = Web3j.build(new HttpService(bscRpc));
        log.info("BEP20 监听任务启动，BSC RPC: {}", bscRpc);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void startWatching() {
        watcherThread = new Thread(this::run, "bep20-blockchain-watcher");
        watcherThread.setDaemon(true);
        watcherThread.start();
        log.info("BEP20 监听任务开始轮询区块...");
    }

    public void run() {
        long nextCheck = 0;
        
        while (!Thread.interrupted()) {
            if (nextCheck <= System.currentTimeMillis()) {
                try {
                    nextCheck = System.currentTimeMillis() + checkInterval;
                    pollBscBlocks();
                } catch (Exception ex) {
                    log.error("BEP20 监听任务异常", ex);
                }
            } else {
                try {
                    Thread.sleep(Math.max(nextCheck - System.currentTimeMillis(), 100));
                } catch (InterruptedException ex) {
                    log.info("BEP20 监听任务被中断");
                    break;
                }
            }
        }
    }

    @PreDestroy
    public void stopWatching() {
        log.info("BEP20 监听任务正在关闭...");
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
     * 每3秒轮询一次BSC区块，解析USDT转账
     */
    public void pollBscBlocks() {
        Timer.Sample timer = metricsService.startScheduledTaskTimer();
        try {
            // 根据配置选择 finalized 或 latest 区块
            DefaultBlockParameter blockParam = payProperties.getTradeIsConfirmed()
                    ? DefaultBlockParameterName.FINALIZED
                    : DefaultBlockParameterName.LATEST;
            EthBlock latestBlock = web3j.ethGetBlockByNumber(blockParam, false).send();
            BigInteger latestBlockNumber = latestBlock.getBlock().getNumber();
            int maxBacklog = 5;
            long startBlock = Math.max(lastScannedBlock + 1, latestBlockNumber.longValue() - maxBacklog + 1);
            if (startBlock > latestBlockNumber.longValue()) {
                return;
            }
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            for (long blockNum = startBlock; blockNum <= latestBlockNumber.longValue(); blockNum++) {
                if (blockNum > lastScannedBlock) {
                    lastScannedBlock = blockNum;
                }
                futures.add(parseBlock(blockNum));
            }
            if (!futures.isEmpty()) {
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            }
        } catch (Exception e) {
            log.error("BEP20 监听任务异常", e);
            metricsService.recordScheduledTaskError("bep20_block_scan", e.getMessage());
        } finally {
            metricsService.stopScheduledTaskTimer(timer, "bep20_block_scan");
        }
    }

    /**
     * 解析指定区块内的BEP20 USDT转账（通过 eth_getBlockByNumber 获取区块，解析交易input）
     */
    public CompletableFuture<Void> parseBlock(long blockNum) {
        Set<String> listenAmount = amountPoolService.getAllLockedAmounts();

        return CompletableFuture.runAsync(() -> {
            try {
                // 使用 eth_getBlockByNumber 获取完整区块数据
                EthBlock block = web3j.ethGetBlockByNumber(
                    DefaultBlockParameter.valueOf(BigInteger.valueOf(blockNum)), 
                    true
                ).send();
                
                if (block.hasError()) {
                    metricsService.incBlockScanFail(ChainType.BEP20);
                    log.error("[BEP20] 区块{} eth_getBlockByNumber失败，code={}, message={}", 
                        blockNum, block.getError().getCode(), block.getError().getMessage());
                    return;
                }
                
                metricsService.incBlockScanSuccess(ChainType.BEP20);
                
                EthBlock.Block blockData = block.getBlock();
                if (blockData == null || blockData.getTransactions() == null) {
                    log.warn("[BEP20] 区块{}无交易数据", blockNum);
                    return;
                }
                
                log.info("[BEP20] 区块{}共{}个交易", blockNum, blockData.getTransactions().size());
                
                // 遍历所有交易
                for (EthBlock.TransactionResult txResult : blockData.getTransactions()) {
                    EthBlock.TransactionObject tx = (EthBlock.TransactionObject) txResult.get();
                    
                    // 检查是否是USDT合约交易
                    if (tx.getTo() != null && tx.getTo().equalsIgnoreCase(chainProperties.getBep20SmartContract())) {
                        String input = tx.getInput();
                        
                        // 检查是否是Transfer方法调用 (0xa9059cbb)
                        if (input != null && input.startsWith("0xa9059cbb") && input.length() >= 74) {
                            try {
                                // 解析Transfer方法的参数
                                // 0xa9059cbb + 32字节to地址 + 32字节amount
                                String toAddressHex = "0x" + input.substring(34, 74); // 去掉前导0
                                String amountHex = input.substring(74, 138);

                                BigInteger amount = new BigInteger(amountHex, 16);
                                // 转换为6位小数精度
                                long usdtAmount = new BigDecimal(amount).divide(new BigDecimal("1000000000000"), 6, java.math.RoundingMode.DOWN).longValue();
                                
                                // 检查是否匹配监听地址和金额
                                if (listenAmount.contains(amountPoolService.buildKey(toAddressHex, usdtAmount))) {
                                    try {
                                        // 查回执，判断交易是否成功
                                        EthGetTransactionReceipt receiptResp = web3j.ethGetTransactionReceipt(tx.getHash()).send();
                                        Optional<TransactionReceipt> receiptOpt = receiptResp.getTransactionReceipt();
                                        if (receiptOpt.isPresent() && receiptOpt.get().isStatusOK()) {
                                            log.info("[BEP20] USDT转账: block={}, to={}, amount={}, txHash={}", 
                                                blockNum, toAddressHex, usdtAmount, tx.getHash());
                                            orderService.markOrderAsPaid(toAddressHex, usdtAmount, tx.getHash());
                                        } else {
                                            log.info("[BEP20] 交易{}回执status!=1，跳过", tx.getHash());
                                        }
                                    } catch (Exception e) {
                                        log.warn("[BEP20] 查询交易{}回执异常: {}", tx.getHash(), e.getMessage());
                                    }
                                }
                            } catch (Exception e) {
                                log.warn("[BEP20] 解析区块{}交易{}的input数据失败: {}", 
                                    blockNum, tx.getHash(), e.getMessage());
                            }
                        }
                    }
                }
                
            } catch (Exception e) {
                metricsService.incBlockScanFail(ChainType.BEP20);
                log.error("[BEP20] 区块{}解析异常: {}", blockNum, e.getMessage());
                metricsService.recordScheduledTaskError("bep20_block_parse", e.getMessage());
            }
        });
    }
} 