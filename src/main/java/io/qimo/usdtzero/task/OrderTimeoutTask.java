package io.qimo.usdtzero.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.qimo.usdtzero.constant.OrderStatus;
import io.qimo.usdtzero.event.CallbackNotifyEvent;
import io.qimo.usdtzero.event.CallbackNotifyMessage;
import io.qimo.usdtzero.model.Order;
import io.qimo.usdtzero.repository.OrderMapper;
import io.qimo.usdtzero.service.AmountPoolService;
import io.qimo.usdtzero.service.LightweightMetricsService;
import io.qimo.usdtzero.service.OrderService;
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
    private LightweightMetricsService metricsService;

    @Autowired
    private OrderService orderService;

    /**
     * 定时检查超时订单
     * 每秒执行一次
     */
    @Scheduled(fixedRate = 1000)
    public void checkTimeoutOrders() {
        // 定时任务耗时指标采集
        io.micrometer.core.instrument.Timer.Sample timer = metricsService.startScheduledTaskTimer();
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
                orderService.processTimeoutOrder(order);
            }
            
        } catch (Exception e) {
            log.error("检查超时订单时发生错误", e);
            metricsService.recordScheduledTaskError("order_timeout", e.getMessage());
        } finally {
            metricsService.stopScheduledTaskTimer(timer, "order_timeout");
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
} 