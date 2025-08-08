package io.qimo.usdtzero.service;

import io.qimo.usdtzero.api.request.CreateOrderRequest;
import io.qimo.usdtzero.config.AppProperties;
import io.qimo.usdtzero.config.PayProperties;
import io.qimo.usdtzero.config.ChainProperties;
import io.qimo.usdtzero.constant.ChainType;
import io.qimo.usdtzero.constant.OrderStatus;
import io.qimo.usdtzero.model.Order;
import io.qimo.usdtzero.repository.OrderMapper;
import io.qimo.usdtzero.service.UsdtRateService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;

import io.qimo.usdtzero.model.BizException;
import io.qimo.usdtzero.model.ErrorCode;
import org.mockito.MockitoAnnotations;
import org.springframework.context.ApplicationEventPublisher;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.isNull;

@Slf4j
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private AppProperties appProperties;

    @Mock
    private PayProperties payProperties;

    @Mock
    private ChainProperties chainProperties;

    @Mock
    private AmountPoolService amountPoolService;

    @Mock
    private LightweightMetricsService metricsService;

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private UsdtRateService usdtRateService;

    @InjectMocks
    private OrderService orderService;

    @BeforeEach
    void setUp() throws NoSuchFieldException, IllegalAccessException {
        MockitoAnnotations.openMocks(this);
        // 不做任何mock，全部放到各自测试方法
        lenient().when(amountPoolService.allocateAmount(anyString(), anyLong())).thenReturn(true);
        lenient().when(amountPoolService.allocateAmount(anyString(), anyLong(), anyString(), any())).thenReturn(true);
        lenient().when(amountPoolService.updateOrderTradeNoAndExpireTime(anyString(), anyLong(), anyString(), any())).thenReturn(true);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        // 反射注入
        Field field = orderService.getClass().getDeclaredField("eventPublisher");
        field.setAccessible(true);
        field.set(orderService, eventPublisher);
    }

    @Test
    void testBasicAmountPrecision() {
        when(appProperties.getUri()).thenReturn("http://localhost:8080");
        when(payProperties.getAtom()).thenReturn("0.01");
        when(payProperties.getScale()).thenReturn(2);
        when(payProperties.getTimeout()).thenReturn(1800);
        when(chainProperties.getSplEnable()).thenReturn(true);
        when(chainProperties.getSplAddress()).thenReturn("test_sol_address");
        when(amountPoolService.isAmountAvailable(any(String.class), any(Long.class))).thenReturn(true);
        when(amountPoolService.allocateAmount(any(String.class), any(Long.class))).thenReturn(true);
        when(orderMapper.insert(any(Order.class))).thenReturn(1);
        CreateOrderRequest dto = new CreateOrderRequest();
        dto.setChainType(ChainType.SPL);
        dto.setAmount(new BigDecimal("100.00"));
        dto.setRate("7.0");
        dto.setSignature("test_signature");
        assertDoesNotThrow(() -> orderService.createOrder(dto));
    }

    @Test
    void testAmountPrecisionEdgeCases() {
        when(appProperties.getUri()).thenReturn("http://localhost:8080");
        when(payProperties.getAtom()).thenReturn("0.01");
        when(payProperties.getScale()).thenReturn(2);
        when(payProperties.getTimeout()).thenReturn(1800);
        when(chainProperties.getSplEnable()).thenReturn(true);
        when(chainProperties.getSplAddress()).thenReturn("test_sol_address");
        when(amountPoolService.isAmountAvailable(any(String.class), any(Long.class))).thenReturn(true);
        when(amountPoolService.allocateAmount(any(String.class), any(Long.class))).thenReturn(true);
        when(orderMapper.insert(any(Order.class))).thenReturn(1);
        BizException ex1 = assertThrows(BizException.class, () -> createOrderWith("0.01", "7.0"));
        assertTrue(ex1.getMessage().contains("usdt金额必须大于0"));
        when(payProperties.getAtom()).thenReturn("0.001");
        when(payProperties.getScale()).thenReturn(3);
        assertDoesNotThrow(() -> createOrderWith("100.00", "7.0"));
    }

    @Test
    void testAmountZeroOrNegativeShouldThrow() {
        // 只mock金额相关依赖，mock chainProperties 使其通过链校验，确保走到金额校验分支
        when(payProperties.getAtom()).thenReturn("0.01");
        when(payProperties.getScale()).thenReturn(2);
        when(payProperties.getTimeout()).thenReturn(1800);
        when(chainProperties.getSplEnable()).thenReturn(true);
        when(chainProperties.getSplAddress()).thenReturn("test_sol_address");
        when(amountPoolService.isAmountAvailable(anyString(), anyLong())).thenReturn(true);
        BizException ex1 = assertThrows(BizException.class, () -> createOrderWith("0", "7.0"));
        assertEquals(ErrorCode.AMOUNT_TOO_SMALL, ex1.getErrorCode());
        assertTrue(ex1.getMessage().contains("usdt金额必须大于0"));
        BizException ex2 = assertThrows(BizException.class, () -> createOrderWith("-1", "7.0"));
        assertEquals(ErrorCode.AMOUNT_TOO_SMALL, ex2.getErrorCode());
        assertTrue(ex2.getMessage().contains("usdt金额必须大于0"));
        BizException ex3 = assertThrows(BizException.class, () -> createOrderWith("0.01", "7.0"));
        assertEquals(ErrorCode.AMOUNT_TOO_SMALL, ex3.getErrorCode());
        assertTrue(ex3.getMessage().contains("usdt金额必须大于0"));
    }

    @Test
    void testAmountPoolAllocateFailShouldThrow() {
        when(payProperties.getAtom()).thenReturn("0.01");
        when(payProperties.getScale()).thenReturn(2);
        when(chainProperties.getSplEnable()).thenReturn(true);
        when(chainProperties.getSplAddress()).thenReturn("test_sol_address");
        when(amountPoolService.isAmountAvailable(any(String.class), any(Long.class))).thenReturn(true);
        when(amountPoolService.allocateAmount(any(String.class), any(Long.class))).thenReturn(false);
        BizException ex = assertThrows(BizException.class, () -> createOrderWith("100", "7.0"));
        assertEquals(ErrorCode.AMOUNT_POOL_ALLOCATE_FAILED, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("该地址附近USDT金额已被占用，请稍后重试"));
    }

    @Test
    void testRateZeroOrNegativeShouldThrow() {

        assertThrows(Exception.class, () -> createOrderWith("100", "0"));
        assertThrows(Exception.class, () -> createOrderWith("100", "-1"));
    }

    @Test
    void testAmountOrRateNullShouldThrow() {
        CreateOrderRequest dto = new CreateOrderRequest();
        dto.setChainType(ChainType.SPL);
        dto.setAmount(null);
        dto.setRate("7.0");
        dto.setSignature("test_signature");
        assertThrows(Exception.class, () -> orderService.createOrder(dto));
        dto.setAmount(new BigDecimal("100"));
        dto.setRate(null);
        assertThrows(Exception.class, () -> orderService.createOrder(dto));
    }

    @Test
    void testChainNotEnabledShouldThrow() {
        when(chainProperties.getSplEnable()).thenReturn(false);
        BizException ex = assertThrows(BizException.class, () -> createOrderWith("100", "7.0"));
        assertEquals(ErrorCode.CHAIN_NOT_ENABLED, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("Solana SPL链未启用"));
    }

    @Test
    void testNoAddressConfiguredShouldThrow() {
        when(chainProperties.getSplEnable()).thenReturn(true);
        when(chainProperties.getSplAddress()).thenReturn("");
        BizException ex = assertThrows(BizException.class, () -> createOrderWith("100", "7.0"));
        assertEquals(ErrorCode.CHAIN_ADDRESS_NOT_CONFIGURED, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("未配置该链类型的收款地址"));
    }

    @Test
    void testAmountPoolReleaseOnException() {
        when(appProperties.getUri()).thenReturn("http://localhost:8080");
        when(payProperties.getAtom()).thenReturn("0.01");
        when(payProperties.getScale()).thenReturn(2);
        when(payProperties.getTimeout()).thenReturn(1800);
        when(payProperties.getTradeIsConfirmed()).thenReturn(false);
        when(chainProperties.getSplEnable()).thenReturn(true);
        when(chainProperties.getSplAddress()).thenReturn("test_sol_address");
        when(amountPoolService.isAmountAvailable(any(String.class), any(Long.class))).thenReturn(true);
        when(amountPoolService.allocateAmount(any(String.class), any(Long.class))).thenReturn(true);
        when(orderMapper.insert(any(Order.class))).thenReturn(0); // 保存失败
        assertThrows(BizException.class, () -> createOrderWith("100", "7.0"));
    }

    @Test
    void testAmountPoolDuplicateAllocation() {
        when(appProperties.getUri()).thenReturn("http://localhost:8080");
        when(payProperties.getAtom()).thenReturn("0.01");
        when(payProperties.getScale()).thenReturn(2);
        when(payProperties.getTimeout()).thenReturn(1800);
        when(chainProperties.getSplEnable()).thenReturn(true);
        when(chainProperties.getSplAddress()).thenReturn("123");
        when(amountPoolService.isAmountAvailable(any(String.class), any(Long.class))).thenReturn(true);
        when(amountPoolService.allocateAmount(any(String.class), any(Long.class))).thenReturn(true);
        when(orderMapper.insert(any(Order.class))).thenReturn(1);
        assertDoesNotThrow(() -> createOrderWith("100", "7.0"));
        when(amountPoolService.isAmountAvailable(any(String.class), any(Long.class))).thenReturn(false);
        BizException ex = assertThrows(BizException.class, () -> createOrderWith("100", "7.0"));
        assertEquals(ErrorCode.AMOUNT_POOL_ALLOCATE_FAILED, ex.getErrorCode());
    }

    @Test
    void testAmountPoolStepAllocation() {
        when(appProperties.getUri()).thenReturn("http://localhost:8080");
        when(payProperties.getAtom()).thenReturn("0.01");
        when(payProperties.getScale()).thenReturn(2);
        when(payProperties.getTimeout()).thenReturn(1800);
        when(chainProperties.getSplEnable()).thenReturn(true);
        when(chainProperties.getSplAddress()).thenReturn("test_sol_address");
        when(amountPoolService.isAmountAvailable(any(String.class), any(Long.class))).thenReturn(false, false, true);
        when(amountPoolService.allocateAmount(any(String.class), any(Long.class))).thenReturn(true);
        when(orderMapper.insert(any(Order.class))).thenReturn(1);
        assertDoesNotThrow(() -> createOrderWith("100", "7.0"));
    }

    @Test
    void testAmountPoolMaxRetriesExceeded() {
        when(payProperties.getAtom()).thenReturn("0.01");
        when(payProperties.getScale()).thenReturn(2);
        when(chainProperties.getSplEnable()).thenReturn(true);
        when(chainProperties.getSplAddress()).thenReturn("test_sol_address");
        final int[] callCount = {0};
        when(amountPoolService.isAmountAvailable(any(String.class), any(Long.class)))
            .thenAnswer(invocation -> {
                callCount[0]++;
                return false;
            });
        BizException ex = assertThrows(BizException.class, () -> createOrderWith("100", "7.0"));
        assertEquals(ErrorCode.AMOUNT_POOL_ALLOCATE_FAILED, ex.getErrorCode());
        assertEquals(100, callCount[0], "应尝试100次分配");
    }

    @Test
    void testMarkOrderAsPaid_amountPoolEntryNull() {
        when(amountPoolService.getEntryByAddressAndAmount(anyString(), anyLong())).thenReturn(null);
        orderService.markOrderAsPaid("addr", 100L, "tx123");
        verify(orderMapper, never()).selectOne(any());
    }

    @Test
    void testMarkOrderAsPaid_orderNull() {
        AmountPoolService.AmountPoolEntry entry = new AmountPoolService.AmountPoolEntry("tradeNo", LocalDateTime.now().plusMinutes(5));
        when(amountPoolService.getEntryByAddressAndAmount(anyString(), anyLong())).thenReturn(entry);
        when(orderMapper.selectOne(any())).thenReturn(null);
        orderService.markOrderAsPaid("addr", 100L, "tx123");
        verify(amountPoolService, never()).releaseAmount(anyString(), anyLong());
    }

    @Test
    void testMarkOrderAsPaid_updateSuccess() {
        AmountPoolService.AmountPoolEntry entry = new AmountPoolService.AmountPoolEntry("tradeNo", LocalDateTime.now().plusMinutes(5));
        Order order = new Order();
        order.setId(1L);
        order.setAddress("addr");
        order.setTradeNo("tradeNo");
        order.setChainType(ChainType.TRC20);
        order.setAmount(100L);
        order.setActualAmount(100L);
        order.setScale(2);
        order.setTxHash("tx123");

        when(amountPoolService.getEntryByAddressAndAmount(anyString(), anyLong())).thenReturn(entry);
        when(orderMapper.selectOne(any())).thenReturn(order);
        when(orderMapper.updateStatusIfMatch(eq(1L), eq(OrderStatus.PENDING), eq(OrderStatus.PAID))).thenReturn(1);
        when(orderMapper.updatePayTimeAndTxHashById(any(), any(),any())).thenReturn(1);
        orderService.markOrderAsPaid("addr", 100L, "tx123");
        
        verify(amountPoolService, times(1)).releaseAmount("addr", 100L);
        verify(metricsService, times(1)).recordPaymentReceived(eq(ChainType.TRC20), eq(100L),eq(100L),eq("tradeNo"));
    }

    @Test
    void testMarkOrderAsPaid_updateFail() {
        AmountPoolService.AmountPoolEntry entry = new AmountPoolService.AmountPoolEntry("tradeNo", LocalDateTime.now().plusMinutes(5));
        Order order = new Order();
        order.setId(1L);
        order.setTradeNo("tradeNo");
        when(amountPoolService.getEntryByAddressAndAmount(anyString(), anyLong())).thenReturn(entry);
        when(orderMapper.selectOne(any())).thenReturn(order);
        when(orderMapper.updateStatusIfMatch(eq(1L), eq(OrderStatus.PENDING), eq(OrderStatus.PAID))).thenReturn(0);
        
        orderService.markOrderAsPaid("addr", 100L, "tx123");
        
        verify(amountPoolService, never()).releaseAmount(anyString(), anyLong());
        verify(orderMapper, never()).update(any(), any());
    }

    private void createOrderWith(String amount, String rate) {
        // 只组装参数，不做任何mock，mock全部放到各自测试方法
        CreateOrderRequest dto = new CreateOrderRequest();
        dto.setChainType(ChainType.SPL);
        dto.setAmount(new BigDecimal(amount));
        dto.setRate(String.valueOf(rate));
        dto.setSignature("test_signature");
        orderService.createOrder(dto);
    }
} 