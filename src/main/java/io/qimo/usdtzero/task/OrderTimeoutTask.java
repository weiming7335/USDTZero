package io.qimo.usdtzero.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.qimo.usdtzero.constant.OrderStatus;
import io.qimo.usdtzero.event.CallbackNotifyEvent;
import io.qimo.usdtzero.event.CallbackNotifyMessage;
import io.qimo.usdtzero.model.Order;
import io.qimo.usdtzero.repository.OrderMapper;
import io.qimo.usdtzero.service.AmountPoolService;
import io.qimo.usdtzero.service.LightweightMetricsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单超时任务
 * 定时检查待支付订单是否超时，更新状态并释放资金池
 */
@Slf4j
@Component
public class OrderTimeoutTask {

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private AmountPoolService amountPoolService;

    @Autowired
    private LightweightMetricsService metricsService;
    @Autowired
    private ApplicationEventPublisher eventPublisher;
    /**
     * 定时检查超时订单
     * 每秒执行一次
     */
    @Scheduled(fixedRate = 1000)
    public void checkTimeoutOrders() {
        log.debug("开始检查超时订单...");
        
        try {
            // 查询所有超时的待支付订单
            List<Order> timeoutOrders = getTimeoutOrders();
            
            if (timeoutOrders.isEmpty()) {
                return;
            }
            
            log.info("发现 {} 个超时订单", timeoutOrders.size());
            
            // 处理每个超时订单
            for (Order order : timeoutOrders) {
                processTimeoutOrder(order);
            }
            
            // 记录埋点 - 超时订单数量
            metricsService.recordScheduledTaskTime(System.currentTimeMillis(), "order_timeout", true);
            
        } catch (Exception e) {
            log.error("检查超时订单时发生错误", e);
            metricsService.recordScheduledTaskError("order_timeout", e.getMessage());
        }
    }

    /**
     * 获取超时的待支付订单
     */
    private List<Order> getTimeoutOrders() {
        LocalDateTime now = LocalDateTime.now();
        
        LambdaQueryWrapper<Order> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Order::getStatus, OrderStatus.PENDING)
                   .lt(Order::getExpireTime, now);
        
        return orderMapper.selectList(queryWrapper);
    }

    /**
     * 处理单个超时订单
     */
    @Transactional(rollbackFor = Exception.class)
    public void processTimeoutOrder(Order order) {
        try {
            log.info("处理超时订单: {}", order.getTradeNo());
            
            // 1. 只有当订单状态仍为PENDING时才更新为EXPIRED
            int updateResult = orderMapper.updateStatusIfMatch(
                order.getId(), 
                OrderStatus.PENDING, 
                OrderStatus.EXPIRED
            );
            
            if (updateResult == 0) {
                log.warn("订单 {} 状态已变更，跳过超时处理", order.getTradeNo());
                return;
            }
            
            // 2. 释放资金池
            if (order.getAddress() != null && order.getActualAmount() != null) {
                amountPoolService.releaseAmount(order.getAddress(), order.getActualAmount());
                log.info("超时订单 {} 资金池释放成功", order.getTradeNo());
            }
            // 发送回调通知事件
            CallbackNotifyMessage notifyMessage = new CallbackNotifyMessage(order.getTradeNo(), OrderStatus.EXPIRED);
            eventPublisher.publishEvent(new CallbackNotifyEvent(this, notifyMessage));
            
        } catch (Exception e) {
            log.error("处理超时订单 {} 时发生错误: {}", order.getTradeNo(), e.getMessage());
        }
    }
} 