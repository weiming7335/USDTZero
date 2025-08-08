package io.qimo.usdtzero.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.qimo.usdtzero.constant.OrderStatus;
import io.qimo.usdtzero.model.Order;
import io.qimo.usdtzero.repository.OrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单初始化服务
 * 在项目启动时处理待支付订单，更新订单状态并重新锁定资金池
 */
@Slf4j
@Service
public class OrderInitializationService implements ApplicationRunner {

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private AmountPoolService amountPoolService;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        log.info("开始初始化待支付订单...");
        
        try {
            // 1. 获取所有待支付订单
            List<Order> pendingOrders = getPendingOrders();
            log.info("找到 {} 个待支付订单", pendingOrders.size());
            
            // 2. 处理每个待支付订单
            int expiredCount = 0;
            int validCount = 0;
            int poolLockedCount = 0;
            int abnormalCount = 0;
            
            for (Order order : pendingOrders) {
                if (processPendingOrder(order)) {
                    expiredCount++;
                } else {
                    validCount++;
                    // 对未过期的订单重新锁定资金池
                    if (lockAmountPool(order)) {
                        poolLockedCount++;
                    } else {
                        // 资金池锁定失败，说明异常，更新为异常状态
                        orderMapper.updateStatusIfMatch(order.getId(), OrderStatus.PENDING, OrderStatus.ABNORMAL);
                        abnormalCount++;
                        log.warn("订单 {} 资金池锁定失败，状态更新为 ABNORMAL", order.getTradeNo());
                    }
                }
            }
            
            log.info("待支付订单初始化完成 - 过期订单: {}, 有效订单: {}, 资金池锁定: {}, 异常订单: {}", 
                expiredCount, validCount, poolLockedCount, abnormalCount);
        } catch (Exception e) {
            log.error("初始化待支付订单时发生错误", e);
        }
    }

    /**
     * 获取所有待支付订单
     */
    private List<Order> getPendingOrders() {
        LambdaQueryWrapper<Order> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Order::getStatus, OrderStatus.PENDING);
        return orderMapper.selectList(queryWrapper);
    }

    /**
     * 处理单个待支付订单
     * @return true表示订单已过期，false表示订单有效
     */
    private boolean processPendingOrder(Order order) {
        try {
            LocalDateTime now = LocalDateTime.now();
            
            // 检查订单是否过期
            if (order.getExpireTime() != null && now.isAfter(order.getExpireTime())) {
                // 订单已过期，更新状态
                orderMapper.updateStatusIfMatch(order.getId(), OrderStatus.PENDING, OrderStatus.EXPIRED);
                log.info("订单 {} 已过期，状态更新为 EXPIRED", order.getTradeNo());
                return true;
            } else {
                // 订单未过期，保持PENDING状态
                log.info("订单 {} 未过期，保持PENDING状态", order.getTradeNo());
                return false;
            }
        } catch (Exception e) {
            log.error("处理订单 {} 时发生错误: {}", order.getTradeNo(), e.getMessage());
            return false;
        }
    }

    /**
     * 锁定资金池
     * @return true表示锁定成功，false表示锁定失败
     */
    private boolean lockAmountPool(Order order) {
        try {
            if (order.getAddress() != null && order.getActualAmount() != null && order.getTradeNo() != null && order.getExpireTime() != null) {
                // 直接分配资金池并初始化订单号和过期时间
                boolean locked = amountPoolService.allocateAmount(order.getAddress(), order.getActualAmount(), order.getTradeNo(), order.getExpireTime());
                if (locked) {
                    log.info("订单 {} 资金池锁定成功: 地址={}, 金额={}",
                            order.getTradeNo(), order.getAddress(), order.getActualAmount());
                    return true;
                } else {
                    log.warn("订单 {} 资金池锁定失败: 地址={}, 金额={} 已被占用",
                            order.getTradeNo(), order.getAddress(), order.getActualAmount());
                    return false;
                }
            } else {
                log.warn("订单 {} 缺少地址、金额、订单号或过期时间信息，无法锁定资金池", order.getTradeNo());
                return false;
            }
        } catch (Exception e) {
            log.error("锁定订单 {} 资金池时发生错误: {}", order.getTradeNo(), e.getMessage());
            return false;
        }
    }
} 