package io.qimo.usdtzero.event;

import io.qimo.usdtzero.constant.ChainType;
import io.qimo.usdtzero.constant.OrderStatus;
import io.qimo.usdtzero.model.Order;
import io.qimo.usdtzero.repository.OrderMapper;
import io.qimo.usdtzero.service.OrderService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
class CallbackNotifyEventListenerIntegrationTest {
    @Autowired
    private ApplicationEventPublisher eventPublisher;
    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private RestTemplate restTemplate;
    @Autowired
    private CallbackNotifyEventListener callbackNotifyEventListener;

    @Test
    void testCallbackNotifyEventListener_sendCallback() {
        // 用 spy 包装 Spring 容器里的 RestTemplate
        RestTemplate spyRestTemplate = Mockito.spy(restTemplate);

        // 用反射注入 spyRestTemplate 到 retryTask
        try {
            java.lang.reflect.Field field = callbackNotifyEventListener.getClass().getDeclaredField("restTemplate");
            field.setAccessible(true);
            field.set(callbackNotifyEventListener, spyRestTemplate);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // mock 行为
        doReturn(ResponseEntity.ok("ok"))
                .when(spyRestTemplate).postForEntity(anyString(), any(), eq(String.class));
        // 构造订单并保存到数据库
        Order order = new Order();
        order.setTradeNo("T987654321");
        order.setNotifyUrl("http://localhost:8080/mock-callback");
        order.setNotifyStatus("PENDING");
        order.setNotifyCount(0);
        order.setAmount(1000L);
        order.setChainType(ChainType.TRC20);
        order.setAddress("123123123");
        order.setStatus(OrderStatus.PAID);
        order.setCreateTime(LocalDateTime.now().minusMinutes(1));
        order.setUpdateTime(LocalDateTime.now());
        orderMapper.deleteByTradeNo(order.getTradeNo());
        orderMapper.insert(order);
        // 发布事件
        CallbackNotifyMessage message = new CallbackNotifyMessage(order.getTradeNo(), OrderStatus.PAID);
        callbackNotifyEventListener.onCallbackNotify(new CallbackNotifyEvent(this, message));
        // 可通过日志或断言数据库状态验证
        verify(spyRestTemplate, timeout(2000).atLeastOnce()).postForEntity(eq(order.getNotifyUrl()), any(), eq(String.class));
    }
}
 