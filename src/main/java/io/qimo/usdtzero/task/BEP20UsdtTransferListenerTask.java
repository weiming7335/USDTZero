package io.qimo.usdtzero.task;

import io.qimo.usdtzero.config.ChainProperties;
import io.qimo.usdtzero.config.PayProperties;
import io.qimo.usdtzero.constant.ChainType;
import io.qimo.usdtzero.service.AmountPoolService;
import io.qimo.usdtzero.service.LightweightMetricsService;
import io.qimo.usdtzero.service.OrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.web3j.crypto.Keys;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.core.methods.response.Log;
import org.web3j.protocol.http.HttpService;
import org.web3j.abi.EventEncoder;
import org.web3j.abi.EventValues;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Event;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Uint256;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.core.methods.response.EthLog;
import java.util.Map;
import java.util.HashMap;

@Slf4j
@Component
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
    private static final int MAX_RECENT_BLOCKS = 64;
    private final ConcurrentSkipListSet<BigInteger> recentScannedBlocks = new ConcurrentSkipListSet<>();
    private long lastScannedBlock = -1L;

    // Transfer(address indexed from, address indexed to, uint256 value)
    private static final Event TRANSFER_EVENT = new Event("Transfer",
            Arrays.asList(
                    TypeReference.create(Address.class, true),
                    TypeReference.create(Address.class, true),
                    TypeReference.create(Uint256.class)));
    private static final String TRANSFER_EVENT_TOPIC = EventEncoder.encode(TRANSFER_EVENT);

    @PostConstruct
    public void init() {
        String bscRpc = chainProperties.getBep20Rpc();
        web3j = Web3j.build(new HttpService(bscRpc));
        log.info("BEP20 监听任务启动，BSC RPC: {}", bscRpc);
    }

    /**
     * 每3秒轮询一次BSC区块，解析USDT转账
     */
    @Scheduled(fixedRate = 3000)
    public void pollBscBlocks() {
        Timer.Sample timer = metricsService.startScheduledTaskTimer();
        try {
            Set<String> listenAmount = amountPoolService.getAllLockedAmounts();
            if (listenAmount.isEmpty()) {
                log.debug("无监听地址，跳过本轮轮询");
                return;
            }
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
                if (recentScannedBlocks.contains(BigInteger.valueOf(blockNum))) continue;
                if (recentScannedBlocks.add(BigInteger.valueOf(blockNum))) {
                    futures.add(parseBlock(blockNum));
                    if (recentScannedBlocks.size() > MAX_RECENT_BLOCKS) {
                        recentScannedBlocks.pollFirst();
                    }
                }
                lastScannedBlock = blockNum;
            }
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        } catch (Exception e) {
            log.error("BEP20 监听任务异常", e);
            metricsService.recordScheduledTaskError("bep20_block_scan", e.getMessage());
        } finally {
            metricsService.stopScheduledTaskTimer(timer, "bep20_block_scan");
        }
    }

    /**
     * 解析指定区块内的BEP20 USDT转账（通过 filter 查询事件，支持并行IO）
     */
    public CompletableFuture<Void> parseBlock(long blockNum) {
        return CompletableFuture.runAsync(() -> {
            Set<String> listenAmount = amountPoolService.getAllLockedAmounts();
            Timer.Sample timer = metricsService.startScheduledTaskTimer();
            try {
                EthFilter filter = new EthFilter(
                        DefaultBlockParameter.valueOf(BigInteger.valueOf(blockNum)),
                        DefaultBlockParameter.valueOf(BigInteger.valueOf(blockNum)),
                        chainProperties.getBep20SmartContract().toLowerCase()
                );
                filter.addSingleTopic(TRANSFER_EVENT_TOPIC);
                EthLog ethLog = web3j.ethGetLogs(filter).send();
                if (ethLog.getLogs() == null || ethLog.getLogs().isEmpty()) {
                    log.warn("区块{}无USDT Transfer事件，filter配置：address={}, topic={},error={}", blockNum, chainProperties.getBep20SmartContract(), TRANSFER_EVENT_TOPIC, ethLog.getError().getMessage());
                    return;
                }
                // 按 txHash 分组
                Map<String, List<org.web3j.protocol.core.methods.response.Log>> txTransferLogs = new HashMap<>();
                for (EthLog.LogResult logResult : ethLog.getLogs()) {
                    org.web3j.protocol.core.methods.response.Log logItem = (org.web3j.protocol.core.methods.response.Log) logResult.get();
                    txTransferLogs.computeIfAbsent(logItem.getTransactionHash(), k -> new ArrayList<>()).add(logItem);
                }
                // 只处理唯一 USDT Transfer 的交易
                for (Map.Entry<String, List<org.web3j.protocol.core.methods.response.Log>> entry : txTransferLogs.entrySet()) {
                    List<org.web3j.protocol.core.methods.response.Log> transferLogs = entry.getValue();
                    if (transferLogs.size() == 1) {
                        org.web3j.protocol.core.methods.response.Log logItem = transferLogs.get(0);
                        String to = "0x" + logItem.getTopics().get(2).substring(26);
                        String checksumAddress = Keys.toChecksumAddress(to);
                        // 链上USDT是18位精度，但业务中汇率换算最多只保留到小数点后三位，比如 1 USDT = 7.123 CNY。直接截断后面12位无效数字
                        BigInteger amount = new BigInteger(logItem.getData().substring(2), 16);
                        long usdtAmount = new BigDecimal(amount).divide(new BigDecimal("1000000000000"), 6, java.math.RoundingMode.DOWN).longValue();
                        if (listenAmount.contains(amountPoolService.buildKey(checksumAddress, usdtAmount))) {
                            log.info("唯一USDT转账: block={}, to={}, amount={}, txHash={}", logItem.getBlockNumber(), checksumAddress, usdtAmount, entry.getKey());
                            orderService.markOrderAsPaid(to, amount.longValue(), entry.getKey());
                            metricsService.recordPaymentReceived(ChainType.BEP20, amount.toString());
                        }
                    }
                }
            } catch (Exception e) {
                log.error("解析BEP20区块{}时发生异常", blockNum, e);
                metricsService.recordScheduledTaskError("bep20_block_parse", e.getMessage());
            } finally {
                metricsService.stopScheduledTaskTimer(timer, "bep20_block_parse");
            }
        });
    }
} 