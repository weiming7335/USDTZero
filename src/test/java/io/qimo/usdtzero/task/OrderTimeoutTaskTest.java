package io.qimo.usdtzero.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.qimo.usdtzero.constant.OrderStatus;
import io.qimo.usdtzero.model.Order;
import io.qimo.usdtzero.repository.OrderMapper;
import io.qimo.usdtzero.service.AmountPoolService;
import io.qimo.usdtzero.service.LightweightMetricsService;
import io.qimo.usdtzero.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doCallRealMethod;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class OrderTimeoutTaskTest {

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private AmountPoolService amountPoolService;

    @Mock
    private LightweightMetricsService metricsService;

    private OrderTimeoutTask orderTimeoutTask;
    private OrderService orderService;

    private Order pendingOrder;
    private Order expiredOrder;
    private Order paidOrder;

    @BeforeEach
    void setUp() {
        // 创建待支付订单（未超时）
        pendingOrder = new Order();
        pendingOrder.setId(1L);
        pendingOrder.setTradeNo("PENDING_001");
        pendingOrder.setStatus(OrderStatus.PENDING);
        pendingOrder.setExpireTime(LocalDateTime.now().plusMinutes(5));
        pendingOrder.setAddress("TRC20_ADDRESS");
        pendingOrder.setActualAmount(1000000L); // 1 USDT

        // 创建超时订单
        expiredOrder = new Order();
        expiredOrder.setId(2L);
        expiredOrder.setTradeNo("EXPIRED_001");
        expiredOrder.setStatus(OrderStatus.PENDING);
        expiredOrder.setExpireTime(LocalDateTime.now().minusMinutes(5));
        expiredOrder.setAddress("TRC20_ADDRESS");
        expiredOrder.setActualAmount(2000000L); // 2 USDT

        // 创建已支付订单
        paidOrder = new Order();
        paidOrder.setId(3L);
        paidOrder.setTradeNo("PAID_001");
        paidOrder.setStatus(OrderStatus.PAID);
        paidOrder.setExpireTime(LocalDateTime.now().minusMinutes(5));
        paidOrder.setAddress("TRC20_ADDRESS");
        paidOrder.setActualAmount(3000000L); // 3 USDT

        orderService = mock(OrderService.class);
        // 统一反射注入mock依赖
        orderTimeoutTask = new OrderTimeoutTask();
        injectField(orderTimeoutTask, "orderMapper", orderMapper);
        injectField(orderTimeoutTask, "metricsService", metricsService);
        // 默认注入mock orderService（可被单独用例覆盖）
        injectField(orderTimeoutTask, "orderService", orderService);
    }

    @Test
    void testCheckTimeoutOrders_NoTimeoutOrders() {
        // 模拟没有超时订单
        when(orderMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.emptyList());

        // 执行测试
        orderTimeoutTask.checkTimeoutOrders();

        // 验证调用
        verify(orderMapper).selectList(any(LambdaQueryWrapper.class));
        verify(amountPoolService, never()).releaseAmount(anyString(), anyLong());
        verify(metricsService, never()).recordScheduledTaskTime(anyLong(), anyString(), anyBoolean());
    }

    @Test
    void testCheckTimeoutOrders_WithTimeoutOrders() {
        List<Order> timeoutOrders = Arrays.asList(expiredOrder);
        when(orderMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(timeoutOrders);
        doCallRealMethod().when(orderService).processTimeoutOrder(any(Order.class));
        injectField(orderService, "orderMapper", orderMapper);
        injectField(orderService, "amountPoolService", amountPoolService);
        injectField(orderService, "metricsService", metricsService);
        injectField(orderService, "eventPublisher", mock(org.springframework.context.ApplicationEventPublisher.class));
        when(orderMapper.updateStatusIfMatch(eq(2L), eq(OrderStatus.PENDING), eq(OrderStatus.EXPIRED)))
                .thenReturn(1);

        orderTimeoutTask.checkTimeoutOrders();

        verify(orderMapper).selectList(any(LambdaQueryWrapper.class));
        verify(orderMapper).updateStatusIfMatch(eq(2L), eq(OrderStatus.PENDING), eq(OrderStatus.EXPIRED));
        verify(amountPoolService).releaseAmount("TRC20_ADDRESS", 2000000L);
        // verify(metricsService).recordScheduledTaskTime(anyLong(), eq("order_timeout"), eq(true));
    }

    @Test
    void testCheckTimeoutOrders_OrderStatusAlreadyChanged() {
        // 模拟订单状态已变更（比如已支付）
        List<Order> timeoutOrders = Arrays.asList(expiredOrder);
        when(orderMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(timeoutOrders);
        // mock orderService.processTimeoutOrder
        OrderService orderServiceMock = mock(OrderService.class);
        injectField(orderTimeoutTask, "orderService", orderServiceMock);

        // 执行测试
        orderTimeoutTask.checkTimeoutOrders();

        // 验证调用
        verify(orderMapper).selectList(any(LambdaQueryWrapper.class));
        verify(orderServiceMock).processTimeoutOrder(expiredOrder);
        verify(amountPoolService, never()).releaseAmount(anyString(), anyLong());
        // verify(metricsService).recordScheduledTaskTime(anyLong(), eq("order_timeout"), eq(true));
    }

    @Test
    void testCheckTimeoutOrders_MultipleTimeoutOrders() {
        Order expiredOrder2 = new Order();
        expiredOrder2.setId(4L);
        expiredOrder2.setTradeNo("EXPIRED_002");
        expiredOrder2.setStatus(OrderStatus.PENDING);
        expiredOrder2.setExpireTime(LocalDateTime.now().minusMinutes(10));
        expiredOrder2.setAddress("SOL_ADDRESS");
        expiredOrder2.setActualAmount(5000000L); // 5 USDT

        List<Order> timeoutOrders = Arrays.asList(expiredOrder, expiredOrder2);
        when(orderMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(timeoutOrders);
        doCallRealMethod().when(orderService).processTimeoutOrder(any(Order.class));
        injectField(orderService, "orderMapper", orderMapper);
        injectField(orderService, "amountPoolService", amountPoolService);
        injectField(orderService, "metricsService", metricsService);
        injectField(orderService, "eventPublisher", mock(org.springframework.context.ApplicationEventPublisher.class));
        when(orderMapper.updateStatusIfMatch(eq(2L), eq(OrderStatus.PENDING), eq(OrderStatus.EXPIRED)))
                .thenReturn(1);
        when(orderMapper.updateStatusIfMatch(eq(4L), eq(OrderStatus.PENDING), eq(OrderStatus.EXPIRED)))
                .thenReturn(1);

        orderTimeoutTask.checkTimeoutOrders();

        verify(orderMapper).selectList(any(LambdaQueryWrapper.class));
        verify(orderMapper).updateStatusIfMatch(eq(2L), eq(OrderStatus.PENDING), eq(OrderStatus.EXPIRED));
        verify(orderMapper).updateStatusIfMatch(eq(4L), eq(OrderStatus.PENDING), eq(OrderStatus.EXPIRED));
        verify(amountPoolService).releaseAmount("TRC20_ADDRESS", 2000000L);
        verify(amountPoolService).releaseAmount("SOL_ADDRESS", 5000000L);
        // verify(metricsService).recordScheduledTaskTime(anyLong(), eq("order_timeout"), eq(true));
    }

    @Test
    void testCheckTimeoutOrders_OrderMissingAddress() {
        Order orderWithoutAddress = new Order();
        orderWithoutAddress.setId(5L);
        orderWithoutAddress.setTradeNo("NO_ADDRESS_001");
        orderWithoutAddress.setStatus(OrderStatus.PENDING);
        orderWithoutAddress.setExpireTime(LocalDateTime.now().minusMinutes(5));
        orderWithoutAddress.setAddress(null);
        orderWithoutAddress.setActualAmount(1000000L);

        List<Order> timeoutOrders = Arrays.asList(orderWithoutAddress);
        when(orderMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(timeoutOrders);
        doCallRealMethod().when(orderService).processTimeoutOrder(any(Order.class));
        injectField(orderService, "orderMapper", orderMapper);
        injectField(orderService, "amountPoolService", amountPoolService);
        injectField(orderService, "metricsService", metricsService);
        injectField(orderService, "eventPublisher", mock(org.springframework.context.ApplicationEventPublisher.class));
        when(orderMapper.updateStatusIfMatch(eq(5L), eq(OrderStatus.PENDING), eq(OrderStatus.EXPIRED)))
                .thenReturn(1);

        orderTimeoutTask.checkTimeoutOrders();

        verify(orderMapper).selectList(any(LambdaQueryWrapper.class));
        verify(orderMapper).updateStatusIfMatch(eq(5L), eq(OrderStatus.PENDING), eq(OrderStatus.EXPIRED));
        verify(amountPoolService, never()).releaseAmount(anyString(), anyLong());
        // verify(metricsService).recordScheduledTaskTime(anyLong(), eq("order_timeout"), eq(true));
    }

    @Test
    void testCheckTimeoutOrders_OrderMissingAmount() {
        Order orderWithoutAmount = new Order();
        orderWithoutAmount.setId(6L);
        orderWithoutAmount.setTradeNo("NO_AMOUNT_001");
        orderWithoutAmount.setStatus(OrderStatus.PENDING);
        orderWithoutAmount.setExpireTime(LocalDateTime.now().minusMinutes(5));
        orderWithoutAmount.setAddress("TRC20_ADDRESS");
        orderWithoutAmount.setActualAmount(null);

        List<Order> timeoutOrders = Arrays.asList(orderWithoutAmount);
        when(orderMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(timeoutOrders);
        doCallRealMethod().when(orderService).processTimeoutOrder(any(Order.class));
        injectField(orderService, "orderMapper", orderMapper);
        injectField(orderService, "amountPoolService", amountPoolService);
        injectField(orderService, "metricsService", metricsService);
        injectField(orderService, "eventPublisher", mock(org.springframework.context.ApplicationEventPublisher.class));
        when(orderMapper.updateStatusIfMatch(eq(6L), eq(OrderStatus.PENDING), eq(OrderStatus.EXPIRED)))
                .thenReturn(1);

        orderTimeoutTask.checkTimeoutOrders();

        verify(orderMapper).selectList(any(LambdaQueryWrapper.class));
        verify(orderMapper).updateStatusIfMatch(eq(6L), eq(OrderStatus.PENDING), eq(OrderStatus.EXPIRED));
        verify(amountPoolService, never()).releaseAmount(anyString(), anyLong());
        // verify(metricsService).recordScheduledTaskTime(anyLong(), eq("order_timeout"), eq(true));
    }

    @Test
    void testCheckTimeoutOrders_DatabaseException() {
        // 模拟数据库异常
        when(orderMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenThrow(new RuntimeException("Database connection failed"));

        // 执行测试
        orderTimeoutTask.checkTimeoutOrders();

        // 验证调用
        verify(orderMapper).selectList(any(LambdaQueryWrapper.class));
        verify(amountPoolService, never()).releaseAmount(anyString(), anyLong());
        verify(metricsService).recordScheduledTaskError(eq("order_timeout"), anyString());
    }

    @Test
    void testCheckTimeoutOrders_UpdateException() {
        List<Order> timeoutOrders = Arrays.asList(expiredOrder);
        when(orderMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(timeoutOrders);
        doCallRealMethod().when(orderService).processTimeoutOrder(any(Order.class));
        injectField(orderService, "orderMapper", orderMapper);
        injectField(orderService, "amountPoolService", amountPoolService);
        injectField(orderService, "metricsService", metricsService);
        injectField(orderService, "eventPublisher", mock(org.springframework.context.ApplicationEventPublisher.class));
        when(orderMapper.updateStatusIfMatch(eq(2L), eq(OrderStatus.PENDING), eq(OrderStatus.EXPIRED)))
                .thenReturn(0); // 触发 update 失败

        orderTimeoutTask.checkTimeoutOrders();

        verify(metricsService, never()).recordScheduledTaskError(anyString(), anyString());
    }

    @Test
    void testCheckTimeoutOrders_ReleaseAmountException() {
        List<Order> timeoutOrders = Arrays.asList(expiredOrder);
        when(orderMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(timeoutOrders);
        doCallRealMethod().when(orderService).processTimeoutOrder(any(Order.class));
        injectField(orderService, "orderMapper", orderMapper);
        injectField(orderService, "amountPoolService", amountPoolService);
        injectField(orderService, "metricsService", metricsService);
        injectField(orderService, "eventPublisher", mock(org.springframework.context.ApplicationEventPublisher.class));
        when(orderMapper.updateStatusIfMatch(eq(2L), eq(OrderStatus.PENDING), eq(OrderStatus.EXPIRED)))
                .thenReturn(1);
        doThrow(new RuntimeException("Release amount failed")).when(amountPoolService).releaseAmount(anyString(), anyLong());

        orderTimeoutTask.checkTimeoutOrders();

        verify(metricsService, never()).recordScheduledTaskError(anyString(), anyString());
    }

    // 反射注入工具方法
    private static void injectField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
} 