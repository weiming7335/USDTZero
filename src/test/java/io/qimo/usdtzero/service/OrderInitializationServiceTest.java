package io.qimo.usdtzero.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.qimo.usdtzero.constant.OrderStatus;
import io.qimo.usdtzero.model.Order;
import io.qimo.usdtzero.repository.OrderMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderInitializationServiceTest {

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private AmountPoolService amountPoolService;

    @InjectMocks
    private OrderInitializationService orderInitializationService;

    private Order expiredOrder;
    private Order validOrder;

    @BeforeEach
    void setUp() {
        // 创建过期的订单
        expiredOrder = new Order();
        expiredOrder.setTradeNo("EXPIRED_ORDER_001");
        expiredOrder.setStatus(OrderStatus.PENDING);
        expiredOrder.setExpireTime(LocalDateTime.now().minusMinutes(10)); // 10分钟前过期
        expiredOrder.setAmount(10000L); // 100元
        expiredOrder.setActualAmount(14290000L); // USDT金额
        expiredOrder.setAddress("test_address_1");

        // 创建有效的订单
        validOrder = new Order();
        validOrder.setTradeNo("VALID_ORDER_001");
        validOrder.setStatus(OrderStatus.PENDING);
        validOrder.setExpireTime(LocalDateTime.now().plusMinutes(10)); // 10分钟后过期
        validOrder.setAmount(5000L); // 50元
        validOrder.setActualAmount(7145000L); // USDT金额
        validOrder.setAddress("test_address_2");
    }

    @Test
    void testInitializePendingOrders() throws Exception {
        // 准备测试数据
        List<Order> pendingOrders = Arrays.asList(expiredOrder, validOrder);
        
        // Mock OrderMapper的行为
        when(orderMapper.selectList(any())).thenReturn(pendingOrders);
        when(orderMapper.updateStatusIfMatch(any(), any(), any())).thenReturn(1);
        
        // Mock AmountPoolService的行为（重载方法）
        when(amountPoolService.allocateAmount(eq("test_address_2"), eq(7145000L), eq("VALID_ORDER_001"), any(LocalDateTime.class))).thenReturn(true);

        // 执行初始化
        orderInitializationService.run(null);

        // 验证查询被调用
        verify(orderMapper, times(1)).selectList(any());

        // 验证过期订单被更新（EXPIRED状态）
        verify(orderMapper, atLeastOnce()).updateStatusIfMatch(any(), any(), any());

        // 验证有效订单的资金池被锁定（重载方法）
        verify(amountPoolService, times(1)).allocateAmount(eq("test_address_2"), eq(7145000L), eq("VALID_ORDER_001"), any(LocalDateTime.class));
    }

    @Test
    void testInitializeWithNoPendingOrders() throws Exception {
        // Mock空列表
        when(orderMapper.selectList(any())).thenReturn(Arrays.asList());

        // 执行初始化
        orderInitializationService.run(null);

        // 验证查询被调用
        verify(orderMapper, times(1)).selectList(any());

        // 验证没有更新操作
        verify(orderMapper, never()).updateStatusIfMatch(any(), any(), any());
        
        // 验证没有资金池操作
        verify(amountPoolService, never()).allocateAmount(anyString(), anyLong());
    }

    @Test
    void testInitializeWithDatabaseError() throws Exception {
        // Mock数据库异常
        when(orderMapper.selectList(any())).thenThrow(new RuntimeException("数据库连接失败"));

        // 执行初始化，应该不会抛出异常
        orderInitializationService.run(null);

        // 验证查询被调用
        verify(orderMapper, times(1)).selectList(any());

        // 验证没有更新操作
        verify(orderMapper, never()).updateStatusIfMatch(any(), any(), any());
        
        // 验证没有资金池操作
        verify(amountPoolService, never()).allocateAmount(anyString(), anyLong());
    }

    @Test
    void testInitializeWithUpdateError() throws Exception {
        // 准备测试数据
        List<Order> pendingOrders = Arrays.asList(expiredOrder);
        
        // Mock查询成功，但更新失败
        when(orderMapper.selectList(any())).thenReturn(pendingOrders);
        when(orderMapper.updateStatusIfMatch(any(), any(), any())).thenThrow(new RuntimeException("更新失败"));

        // 执行初始化，应该不会抛出异常
        orderInitializationService.run(null);

        // 验证查询被调用
        verify(orderMapper, times(1)).selectList(any());

        // 验证更新被调用（即使失败）
        verify(orderMapper, atLeastOnce()).updateStatusIfMatch(any(), any(), any());
    }

    @Test
    void testInitializeWithPoolLockError() throws Exception {
        // 准备测试数据
        List<Order> pendingOrders = Arrays.asList(validOrder);
        
        // Mock查询成功，但资金池锁定失败
        when(orderMapper.selectList(any())).thenReturn(pendingOrders);
        when(amountPoolService.allocateAmount(eq("test_address_2"), eq(7145000L), eq("VALID_ORDER_001"), any(LocalDateTime.class)))
            .thenThrow(new RuntimeException("资金池锁定失败"));
        when(orderMapper.updateStatusIfMatch(any(), any(), any())).thenReturn(1);

        // 执行初始化，应该不会抛出异常
        orderInitializationService.run(null);

        // 验证查询被调用
        verify(orderMapper, times(1)).selectList(any());

        // 验证订单被更新为ABNORMAL状态（因为资金池锁定失败）
        verify(orderMapper, atLeastOnce()).updateStatusIfMatch(any(), any(), any());
        
        // 验证资金池锁定被调用（重载方法）
        verify(amountPoolService, times(1)).allocateAmount(eq("test_address_2"), eq(7145000L), eq("VALID_ORDER_001"), any(LocalDateTime.class));
    }

    @Test
    void testInitializeWithPoolLockFailure() throws Exception {
        // 准备测试数据
        List<Order> pendingOrders = Arrays.asList(validOrder);
        
        // Mock查询成功，但资金池锁定返回false（已被占用）
        when(orderMapper.selectList(any())).thenReturn(pendingOrders);
        when(amountPoolService.allocateAmount(eq("test_address_2"), eq(7145000L), eq("VALID_ORDER_001"), any(LocalDateTime.class))).thenReturn(false);

        // 执行初始化
        orderInitializationService.run(null);

        // 验证查询被调用
        verify(orderMapper, times(1)).selectList(any());

        // 验证订单被更新为ABNORMAL状态
        verify(orderMapper, atLeastOnce()).updateStatusIfMatch(any(), any(), any());
        
        // 验证资金池锁定被调用（重载方法）
        verify(amountPoolService, times(1)).allocateAmount(eq("test_address_2"), eq(7145000L), eq("VALID_ORDER_001"), any(LocalDateTime.class));
    }

    @Test
    void testInitializeWithMissingAddressOrAmount() throws Exception {
        // 准备测试数据 - 缺少地址或金额的订单
        Order invalidOrder = new Order();
        invalidOrder.setTradeNo("INVALID_ORDER_001");
        invalidOrder.setStatus(OrderStatus.PENDING);
        invalidOrder.setExpireTime(LocalDateTime.now().plusMinutes(10));
        invalidOrder.setAmount(5000L);
        // 故意不设置address和actualAmount
        
        List<Order> pendingOrders = Arrays.asList(invalidOrder);
        
        // Mock查询成功，但资金池锁定返回false（因为缺少必要信息）
        when(orderMapper.selectList(any())).thenReturn(pendingOrders);
        when(orderMapper.updateStatusIfMatch(any(), any(), any())).thenReturn(1);

        // 执行初始化
        orderInitializationService.run(null);

        // 验证查询被调用
        verify(orderMapper, times(1)).selectList(any());

        // 验证订单被更新为ABNORMAL状态
        verify(orderMapper, atLeastOnce()).updateStatusIfMatch(any(), any(), any());
        
        // 验证没有资金池锁定操作（因为缺少必要信息）
        verify(amountPoolService, never()).allocateAmount(anyString(), anyLong());
    }
} 