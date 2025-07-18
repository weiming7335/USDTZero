package io.qimo.usdtzero.service;

import io.qimo.usdtzero.api.request.CreateOrderRequest;
import io.qimo.usdtzero.config.AppProperties;
import io.qimo.usdtzero.config.PayProperties;
import io.qimo.usdtzero.config.ChainProperties;
import io.qimo.usdtzero.constant.ChainType;
import io.qimo.usdtzero.model.Order;
import io.qimo.usdtzero.repository.OrderMapper;
import io.qimo.usdtzero.task.UsdtRateTask;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import io.qimo.usdtzero.model.BizException;
import io.qimo.usdtzero.model.ErrorCode;

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
    private UsdtRateTask usdtRateTask;

    @InjectMocks
    private OrderService orderService;

    @BeforeEach
    void setUp() {
        // 不做任何mock，全部放到各自测试方法
    }

    @Test
    void testBasicAmountPrecision() {
        when(appProperties.getUri()).thenReturn("http://localhost:8080");
        when(payProperties.getUsdtAtom()).thenReturn("0.01");
        when(payProperties.getUsdtScale()).thenReturn(2);
        when(payProperties.getExpireTime()).thenReturn(1800);
        when(chainProperties.getSolEnable()).thenReturn(true);
        when(chainProperties.getSolAddress()).thenReturn("test_sol_address");
        when(amountPoolService.isAmountAvailable(any(String.class), any(Long.class))).thenReturn(true);
        when(amountPoolService.allocateAmount(any(String.class), any(Long.class))).thenReturn(true);
        when(orderMapper.insert(any(Order.class))).thenReturn(1);
        CreateOrderRequest dto = new CreateOrderRequest();
        dto.setChainType(ChainType.SOL);
        dto.setAmount(new BigDecimal("100.00"));
        dto.setUsdtRate("7.0");
        dto.setSignature("test_signature");
        assertDoesNotThrow(() -> orderService.createOrder(dto));
    }

    @Test
    void testAmountPrecisionEdgeCases() {
        when(appProperties.getUri()).thenReturn("http://localhost:8080");
        when(payProperties.getUsdtAtom()).thenReturn("0.01");
        when(payProperties.getUsdtScale()).thenReturn(2);
        when(payProperties.getExpireTime()).thenReturn(1800);
        when(chainProperties.getSolEnable()).thenReturn(true);
        when(chainProperties.getSolAddress()).thenReturn("test_sol_address");
        when(amountPoolService.isAmountAvailable(any(String.class), any(Long.class))).thenReturn(true);
        when(amountPoolService.allocateAmount(any(String.class), any(Long.class))).thenReturn(true);
        when(orderMapper.insert(any(Order.class))).thenReturn(1);
        // 不能分配出最小单位的用 assertThrows
        BizException ex1 = assertThrows(BizException.class, () -> createOrderWith("0.01", "7.0"));
        assertTrue(ex1.getMessage().contains("usdt金额必须大于0"));
        // 测试不同精度
        when(payProperties.getUsdtAtom()).thenReturn("0.001");
        when(payProperties.getUsdtScale()).thenReturn(3);
        assertDoesNotThrow(() -> createOrderWith("100.00", "7.0"));
    }

    @Test
    void testAmountZeroOrNegativeShouldThrow() {
        // 金额为0.01时，mock依赖
        UsdtRateTask.setRate(new BigDecimal("7.0"));
        when(payProperties.getUsdtAtom()).thenReturn("0.01");
        when(payProperties.getUsdtScale()).thenReturn(2);
        when(chainProperties.getSolEnable()).thenReturn(true);
        when(chainProperties.getSolAddress()).thenReturn("test_sol_address");
        when(amountPoolService.isAmountAvailable(any(String.class), any(Long.class))).thenReturn(true);
        when(amountPoolService.allocateAmount(any(String.class), any(Long.class))).thenReturn(true);
        // 金额为0、-1，不需要mock任何依赖
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
    void testRateZeroOrNegativeShouldThrow() {
        // 不需要mock payProperties/chainProperties，因为直接抛异常
        assertThrows(Exception.class, () -> createOrderWith("100", "0"));
        assertThrows(Exception.class, () -> createOrderWith("100", "-1"));
    }

    @Test
    void testAmountOrRateNullShouldThrow() {
        // 不需要mock payProperties/chainProperties，因为直接抛异常
        CreateOrderRequest dto = new CreateOrderRequest();
        dto.setChainType(ChainType.SOL);
        dto.setAmount(null);
        dto.setUsdtRate("7.0");
        dto.setSignature("test_signature");
        assertThrows(Exception.class, () -> orderService.createOrder(dto));
        dto.setAmount(new BigDecimal("100"));
        dto.setUsdtRate(null);
        assertThrows(Exception.class, () -> orderService.createOrder(dto));
    }

    @Test
    void testChainNotEnabledShouldThrow() {
        when(chainProperties.getSolEnable()).thenReturn(false);
        BizException ex = assertThrows(BizException.class, () -> createOrderWith("100", "7.0"));
        assertEquals(ErrorCode.CHAIN_NOT_ENABLED, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("Solana链未启用"));
    }

    @Test
    void testNoAddressConfiguredShouldThrow() {
        when(chainProperties.getSolEnable()).thenReturn(true);
        when(chainProperties.getSolAddress()).thenReturn("");
        BizException ex = assertThrows(BizException.class, () -> createOrderWith("100", "7.0"));
        assertEquals(ErrorCode.CHAIN_ADDRESS_NOT_CONFIGURED, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("未配置该链类型的收款地址"));
    }

    @Test
    void testAmountPoolAllocateFailShouldThrow() {
        when(payProperties.getUsdtAtom()).thenReturn("0.01");
        when(chainProperties.getSolEnable()).thenReturn(true);
        when(chainProperties.getSolAddress()).thenReturn("test_sol_address");
        // 精确模拟100次都失败
        when(amountPoolService.isAmountAvailable(any(String.class), any(Long.class)))
            .thenAnswer(invocation -> true);
        when(amountPoolService.allocateAmount(any(String.class), any(Long.class)))
            .thenAnswer(invocation -> false);
        BizException ex = assertThrows(BizException.class, () -> createOrderWith("100", "7.0"));
        assertEquals(ErrorCode.AMOUNT_POOL_ALLOCATE_FAILED, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("该地址附近USDT金额已被占用，请稍后重试"));
    }

    @Test
    void testOrderSaveFailShouldThrow() {
        when(appProperties.getUri()).thenReturn("http://localhost:8080");
        when(payProperties.getUsdtAtom()).thenReturn("0.01");
        when(payProperties.getUsdtScale()).thenReturn(2);
        when(payProperties.getExpireTime()).thenReturn(1800);
        when(payProperties.getTradeIsConfirmed()).thenReturn(false);
        when(chainProperties.getSolEnable()).thenReturn(true);
        when(chainProperties.getSolAddress()).thenReturn("test_sol_address");
        when(amountPoolService.isAmountAvailable(any(String.class), any(Long.class))).thenReturn(true);
        when(amountPoolService.allocateAmount(any(String.class), any(Long.class))).thenReturn(true);
        when(orderMapper.insert(any(Order.class))).thenReturn(0);
        assertThrows(BizException.class, () -> createOrderWith("100", "7.0"));
    }

    @Test
    void testAmountPoolAllocateAndRelease() {
        when(appProperties.getUri()).thenReturn("http://localhost:8080");
        when(payProperties.getUsdtAtom()).thenReturn("0.01");
        when(payProperties.getUsdtScale()).thenReturn(2);
        when(payProperties.getExpireTime()).thenReturn(1800);
        when(chainProperties.getSolEnable()).thenReturn(true);
        when(chainProperties.getSolAddress()).thenReturn("test_sol_address");
        when(amountPoolService.isAmountAvailable(any(String.class), any(Long.class))).thenReturn(true);
        when(amountPoolService.allocateAmount(any(String.class), any(Long.class))).thenReturn(true);
        when(orderMapper.insert(any(Order.class))).thenReturn(1);
        assertDoesNotThrow(() -> createOrderWith("100", "7.0"));
    }

    @Test
    void testAmountPoolReleaseOnException() {
        when(appProperties.getUri()).thenReturn("http://localhost:8080");
        when(payProperties.getUsdtAtom()).thenReturn("0.01");
        when(payProperties.getUsdtScale()).thenReturn(2);
        when(payProperties.getExpireTime()).thenReturn(1800);
        when(payProperties.getTradeIsConfirmed()).thenReturn(false);
        when(chainProperties.getSolEnable()).thenReturn(true);
        when(chainProperties.getSolAddress()).thenReturn("test_sol_address");
        when(amountPoolService.isAmountAvailable(any(String.class), any(Long.class))).thenReturn(true);
        when(amountPoolService.allocateAmount(any(String.class), any(Long.class))).thenReturn(true);
        when(orderMapper.insert(any(Order.class))).thenReturn(0); // 保存失败
        assertThrows(BizException.class, () -> createOrderWith("100", "7.0"));
    }

    @Test
    void testAmountPoolDuplicateAllocation() {
        when(appProperties.getUri()).thenReturn("http://localhost:8080");
        when(payProperties.getUsdtAtom()).thenReturn("0.01");
        when(payProperties.getUsdtScale()).thenReturn(2);
        when(payProperties.getExpireTime()).thenReturn(1800);
        when(chainProperties.getSolEnable()).thenReturn(true);
        when(chainProperties.getSolAddress()).thenReturn("123");
        when(amountPoolService.isAmountAvailable(any(String.class), any(Long.class))).thenReturn(true);
        when(amountPoolService.allocateAmount(any(String.class), any(Long.class))).thenReturn(true);
        when(orderMapper.insert(any(Order.class))).thenReturn(1);
        assertDoesNotThrow(() -> createOrderWith("100", "7.0"));
        when(amountPoolService.isAmountAvailable(any(String.class), any(Long.class))).thenReturn(false);
        assertThrows(BizException.class, () -> createOrderWith("100", "7.0"));
    }

    @Test
    void testAmountPoolStepAllocation() {
        when(appProperties.getUri()).thenReturn("http://localhost:8080");
        when(payProperties.getUsdtAtom()).thenReturn("0.01");
        when(payProperties.getUsdtScale()).thenReturn(2);
        when(payProperties.getExpireTime()).thenReturn(1800);
        when(chainProperties.getSolEnable()).thenReturn(true);
        when(chainProperties.getSolAddress()).thenReturn("test_sol_address");
        when(amountPoolService.isAmountAvailable(any(String.class), any(Long.class))).thenReturn(false, false, true);
        when(amountPoolService.allocateAmount(any(String.class), any(Long.class))).thenReturn(true);
        when(orderMapper.insert(any(Order.class))).thenReturn(1);
        assertDoesNotThrow(() -> createOrderWith("100", "7.0"));
    }

    @Test
    void testAmountPoolMaxRetriesExceeded() {
        when(payProperties.getUsdtAtom()).thenReturn("0.01");
        when(chainProperties.getSolEnable()).thenReturn(true);
        when(chainProperties.getSolAddress()).thenReturn("test_sol_address");
        final int[] callCount = {0};
        when(amountPoolService.isAmountAvailable(any(String.class), any(Long.class)))
            .thenAnswer(invocation -> {
                callCount[0]++;
                return false;
            });
        assertThrows(BizException.class, () -> createOrderWith("100", "7.0"));
        assertEquals(100, callCount[0], "应尝试100次分配");
    }

    private void createOrderWith(String amount, String rate) {
        CreateOrderRequest dto = new CreateOrderRequest();
        dto.setChainType(ChainType.SOL);
        dto.setAmount(new BigDecimal(amount));
        dto.setUsdtRate(rate);
        dto.setSignature("test_signature");
        orderService.createOrder(dto);
    }
} 