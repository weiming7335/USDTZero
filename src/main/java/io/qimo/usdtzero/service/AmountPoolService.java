package io.qimo.usdtzero.service;

import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 金额池服务，基于ConcurrentHashMap.newKeySet()实现，支持高并发金额分配与释放。
 * 使用Set存储address_amount组合，确保同一地址同一金额只能被分配一次。
 */
@Service
public class AmountPoolService {
    // 使用线程安全的Set存储address_amount组合
    private final Set<String> amountPool = ConcurrentHashMap.newKeySet();

    /**
     * 分配金额（返回true表示分配成功）
     */
    public boolean allocateAmount(String address, long amount) {
        String key = buildKey(address, amount);
        return amountPool.add(key);
    }

    /**
     * 释放金额
     */
    public void releaseAmount(String address, long amount) {
        String key = buildKey(address, amount);
        amountPool.remove(key);
    }

    /**
     * 查询金额是否可用
     */
    public boolean isAmountAvailable(String address, long amount) {
        String key = buildKey(address, amount);
        return !amountPool.contains(key);
    }

    /**
     * 构建唯一key
     */
    private String buildKey(String address, long amount) {
        return address + "_" + amount;
    }
} 