package io.qimo.usdtzero.task;

import io.qimo.usdtzero.constant.ChainType;
import io.qimo.usdtzero.model.Order;
import io.qimo.usdtzero.repository.OrderMapper;
import io.qimo.usdtzero.service.OrderService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
class CallbackNotifyRetryTaskTest {
    @Autowired
    private CallbackNotifyRetryTask retryTask;
    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private RestTemplate restTemplate;
    @Test
    void testRetryFailedNotify() {

        // 用 spy 包装 Spring 容器里的 RestTemplate
        RestTemplate spyRestTemplate = Mockito.spy(restTemplate);

        // 用反射注入 spyRestTemplate 到 retryTask
        try {
            java.lang.reflect.Field field = retryTask.getClass().getDeclaredField("restTemplate");
            field.setAccessible(true);
            field.set(retryTask, spyRestTemplate);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // mock 行为
        doReturn(ResponseEntity.ok("ok"))
                .when(spyRestTemplate).postForEntity(anyString(), any(), eq(String.class));
        // 构造一个需要重试的订单
        Order order = new Order();
        order.setTradeNo("T555555555");
        order.setNotifyUrl("http://localhost:8080/mock-callback");
        order.setNotifyStatus("RETRY");
        order.setNotifyCount(1);
        order.setStatus("PAID");
        order.setAmount(1000L);
        order.setChainType(ChainType.TRC20);
        order.setAddress("123123123");
        order.setCreateTime(LocalDateTime.now().minusMinutes(10));
        order.setLastNotifyTime(LocalDateTime.now().minusMinutes(5));
        order.setUpdateTime(LocalDateTime.now());
        orderMapper.deleteByTradeNo(order.getTradeNo());
        orderMapper.insert(order);

            // 执行重试任务
            retryTask.retryFailedNotify();
            // 验证回调被调用
        verify(spyRestTemplate, timeout(2000).atLeastOnce())
                .postForEntity(eq(order.getNotifyUrl()), any(), eq(String.class));

    }
} 