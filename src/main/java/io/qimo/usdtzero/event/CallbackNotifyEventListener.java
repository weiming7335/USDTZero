package io.qimo.usdtzero.event;

import io.qimo.usdtzero.constant.NotifyStatus;
import io.qimo.usdtzero.service.OrderService;
import io.qimo.usdtzero.model.Order;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.event.TransactionPhase;

import java.time.LocalDateTime;
import io.qimo.usdtzero.event.CallbackNotifyMessage;

@Slf4j
@Component
public class CallbackNotifyEventListener {
    @Autowired
    private OrderService orderService;
    @Autowired
    private RestTemplate restTemplate;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCallbackNotify(CallbackNotifyEvent event) {
        String tradeNo = event.getMessage().getTradeNo();
        Order order = orderService.getByTradeNo(tradeNo);
        if (order == null) {
            log.warn("订单不存在，无法回调通知，tradeNo={}", tradeNo);
            return;
        }
        // 只在订单通知状态为PENDING且notifyUrl不为空时才回调
        if (!NotifyStatus.PENDING.equals(order.getNotifyStatus()) || StringUtils.isBlank(order.getNotifyUrl())) {
            log.info("订单不在待通知状态或回调地址为空，跳过回调，tradeNo={}, notifyStatus={}, notifyUrl={}", tradeNo, order.getNotifyStatus(), order.getNotifyUrl());
            return;
        }
        int notifyCount = order.getNotifyCount() == null ? 0 : order.getNotifyCount();
        notifyCount++;
        LocalDateTime now = LocalDateTime.now();
        String notifyStatus;
        try {
            // 发送 HTTP POST 回调，只发送 tradeNo 和 notifyType
            CallbackNotifyMessage message = new CallbackNotifyMessage(order.getTradeNo(), event.getMessage().getStatus());
            log.info("发送回调通知，tradeNo={}, message={}", order.getTradeNo(), message);
            ResponseEntity<String> response = restTemplate.postForEntity(order.getNotifyUrl(), message, String.class);
            if (response.getStatusCode().is2xxSuccessful()) {
                notifyStatus = NotifyStatus.SUCCESS;
                log.info("回调通知成功，tradeNo={}, url={}, message={}", order.getTradeNo(), order.getNotifyUrl(), message);
            } else {
                notifyStatus = NotifyStatus.RETRY;
                log.warn("回调通知失败，tradeNo={}, url={}, code={}, message={}", order.getTradeNo(), order.getNotifyUrl(), response.getStatusCodeValue(), message);
            }
        } catch (Exception e) {
            notifyStatus = NotifyStatus.RETRY;
            log.error("回调通知异常，tradeNo={}, url={}, error={}", order.getTradeNo(), order.getNotifyUrl(), e.getMessage());
        }
        // 更新订单通知状态
        orderService.updateOrderNotifyInfo(order.getId(), notifyCount, notifyStatus, now);
    }
} 