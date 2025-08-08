package io.qimo.usdtzero.service;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 金额池服务，基于ConcurrentHashMap实现，支持高并发金额分配与释放。
 * 使用Map存储address_amount到AmountPoolEntry的映射，确保同一地址同一金额只能被分配一次。
 */
@Service
public class AmountPoolService {
    // 金额池条目对象
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AmountPoolEntry {
        private String orderTradeNo;
        private LocalDateTime expireTime;
}

    // 使用线程安全的Map存储address_amount到AmountPoolEntry的映射
    private final Map<String, AmountPoolEntry> amountPool = new ConcurrentHashMap<>();

    /**
     * 分配金额（原有方法）
     * @param address 地址
     * @param amount 金额
     * @return 是否分配成功
     */
    public boolean allocateAmount(String address, long amount) {
        String key = buildKey(address, amount);
        return amountPool.putIfAbsent(key, new AmountPoolEntry()) == null;
    }

    /**
     * 分配金额（带订单号和过期时间）
     * @param address 地址
     * @param amount 金额
     * @param orderTradeNo 订单号
     * @param expireTime 过期时间
     * @return 是否分配成功
     */
    public boolean allocateAmount(String address, long amount, String orderTradeNo, LocalDateTime expireTime) {
        String key = buildKey(address, amount);
        return amountPool.putIfAbsent(key, new AmountPoolEntry(orderTradeNo, expireTime)) == null;
    }

    /**
     * 释放金额
     * @param address 地址
     * @param amount 金额
     */
    public void releaseAmount(String address, long amount) {
        String key = buildKey(address, amount);
        amountPool.remove(key);
    }

    /**
     * 通过唯一key直接释放金额（用于兜底清理）
     * @param key 唯一key
     */
    public void releaseAmountByKey(String key) {
        amountPool.remove(key);
    }

    /**
     * 查询金额是否可用
     * @param address 地址
     * @param amount 金额
     * @return 是否可用
     */
    public boolean isAmountAvailable(String address, long amount) {
        String key = buildKey(address, amount);
        return !amountPool.containsKey(key);
    }

    /**
     * 获取所有锁定的金额
     * @return 锁定的金额集合
     */
    public Set<String> getAllLockedAmounts() {
        return amountPool.keySet();
    }

    /**
     * 获取资金池与订单号和过期时间的关联关系
     * @return 关联关系Map
     */
    public Map<String, AmountPoolEntry> getAmountToOrderMap() {
        return new ConcurrentHashMap<>(amountPool);
    }

    /**
     * 更新资金池中金额的订单号和过期时间关联
     * @param address 地址
     * @param amount 金额
     * @param orderTradeNo 新的订单号
     * @param expireTime 过期时间
     * @return 是否更新成功
     */
    public boolean updateOrderTradeNoAndExpireTime(String address, long amount, String orderTradeNo, LocalDateTime expireTime) {
        String key = buildKey(address, amount);
        // put时value为null，更新时需要新建对象
        AmountPoolEntry entry = new AmountPoolEntry(orderTradeNo, expireTime);
        amountPool.put(key, entry);
        return true;
    }

    /**
     * 构建唯一key
     * @param address 地址
     * @param amount 金额
     * @return 唯一key
     */
    public String buildKey(String address, long amount) {
        return address + "_" + amount;
    }

    /**
     * 通过地址和金额获取金额池条目
     * @param address 地址
     * @param amount 金额
     * @return 对应的金额池条目，若不存在则返回 null
     */
    public AmountPoolEntry getEntryByAddressAndAmount(String address, long amount) {
        String key = buildKey(address, amount);
        return amountPool.get(key);
    }
} 