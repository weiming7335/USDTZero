package io.qimo.usdtzero.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.qimo.usdtzero.constant.NotifyStatus;
import io.qimo.usdtzero.constant.OrderStatus;
import io.qimo.usdtzero.event.CallbackNotifyEvent;
import io.qimo.usdtzero.event.CallbackNotifyMessage;
import io.qimo.usdtzero.model.Order;
import io.qimo.usdtzero.repository.OrderMapper;
import io.qimo.usdtzero.service.OrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import io.qimo.usdtzero.service.LightweightMetricsService;

@Component
@Slf4j
public class CallbackNotifyRetryTask {
    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private RestTemplate restTemplate;
    @Autowired
    private OrderService orderService;
    @Autowired
    private LightweightMetricsService metricsService;

    // 每3秒执行一次
    @Scheduled(fixedRate = 3000)
    public void retryFailedNotify() {
        io.micrometer.core.instrument.Timer.Sample timer = metricsService.startScheduledTaskTimer();
        try {
            // 查询需要重试的订单（notifyStatus=RETRY，且重试次数未超限，且到达下次重试时间）
            List<Order> retryOrders = orderMapper.selectList(
                    new LambdaQueryWrapper<Order>()
                            .eq(Order::getNotifyStatus, NotifyStatus.RETRY)
                            .lt(Order::getNotifyCount, 6)
                            .in(Order::getStatus, OrderStatus.PAID, OrderStatus.EXPIRED)
                            .ge(Order::getLastNotifyTime, LocalDateTime.now().minusMinutes(32))
            );
            for (Order order : retryOrders) {
                int retryCount = order.getNotifyCount() == null ? 0 : order.getNotifyCount();
                int maxRetryCount = 6;
                // 计算下次重试时间（0,1,2,4,8,16分钟...）
                int[] retryIntervals = {0, 1, 2, 4, 8, 16};
                int interval = retryCount < retryIntervals.length ? retryIntervals[retryCount] : retryIntervals[retryIntervals.length - 1];
                LocalDateTime lastNotifyTime = order.getLastNotifyTime() == null ? order.getCreateTime() : order.getLastNotifyTime();
                LocalDateTime nextRetryTime = lastNotifyTime.plusMinutes(interval);
                // 判断是否到达重试时间
                if (LocalDateTime.now().isBefore(nextRetryTime)) {
                    continue;
                }
                CallbackNotifyMessage message = new CallbackNotifyMessage(order.getTradeNo(), order.getStatus());
                String notifyStatus;
                LocalDateTime now = LocalDateTime.now();
                try {
                    ResponseEntity<String> response = restTemplate.postForEntity(order.getNotifyUrl(), message, String.class);
                    if (response.getStatusCode().is2xxSuccessful()) {
                        notifyStatus = NotifyStatus.SUCCESS;
                    } else {
                        notifyStatus = (retryCount + 1) >= maxRetryCount ? NotifyStatus.MAX_RETRY : NotifyStatus.RETRY;
                    }
                } catch (Exception e) {
                    log.error("回调通知异常，orderId={}, url={}, error={}", order.getId(), order.getNotifyUrl(), e.getMessage());
                    notifyStatus = (retryCount + 1) >= maxRetryCount ? NotifyStatus.MAX_RETRY : NotifyStatus.RETRY;
                }
                orderService.updateOrderNotifyInfo(order.getId(), retryCount + 1, notifyStatus, now);
            }
        } finally {
            metricsService.stopScheduledTaskTimer(timer, "callback_notify_retry");
        }
    }
} 