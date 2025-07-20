package io.qimo.usdtzero.service;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.qimo.usdtzero.api.request.CreateOrderRequest;
import io.qimo.usdtzero.api.request.CancelOrderRequest;
import io.qimo.usdtzero.api.response.CreateOrderResponse;
import io.qimo.usdtzero.api.response.CancelOrderResponse;
import io.qimo.usdtzero.config.AppProperties;
import io.qimo.usdtzero.config.PayProperties;
import io.qimo.usdtzero.config.ChainProperties;
import io.qimo.usdtzero.constant.OrderStatus;
import io.qimo.usdtzero.constant.ChainType;
import io.qimo.usdtzero.constant.NotifyStatus;
import io.qimo.usdtzero.model.BizException;
import io.qimo.usdtzero.model.ErrorCode;
import io.qimo.usdtzero.model.Order;
import io.qimo.usdtzero.repository.OrderMapper;
import io.qimo.usdtzero.util.UsdtRateUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.UUID;
import io.qimo.usdtzero.service.UsdtRateService;

@Slf4j
@Service
public class OrderService {
    @Autowired
    private AppProperties appProperties;
    @Autowired
    private PayProperties payProperties;
    @Autowired
    private ChainProperties chainProperties;
    @Autowired
    private AmountPoolService amountPoolService;
    @Autowired
    private LightweightMetricsService metricsService;
    @Autowired
    private OrderMapper orderMapper;


    /**
     * 根据链类型获取收款地址，判断链是否启用
     */
    private String getAddressByChainType(String chainType) {
        if (StringUtils.isBlank(chainType)) return "";
        switch (chainType.toLowerCase()) {
            case ChainType.TRC20:
                if (Boolean.FALSE.equals(chainProperties.getTrc20Enable())) {
                    throw new BizException(ErrorCode.CHAIN_NOT_ENABLED, "TRC20链未启用");
                }
                if (StringUtils.isNotBlank(chainProperties.getTrc20Address())) {
                    return chainProperties.getTrc20Address();
                }
                break;
            case ChainType.SOL:
                if (Boolean.FALSE.equals(chainProperties.getSolEnable())) {
                    throw new BizException(ErrorCode.CHAIN_NOT_ENABLED, "Solana链未启用");
                }
                if (StringUtils.isNotBlank(chainProperties.getSolAddress())) {
                    return chainProperties.getSolAddress();
                }
                break;
            default:
                break;
        }
        return null;
    }

    /**
     * 创建订单业务
     */
    @Transactional(rollbackFor = Exception.class)
    public CreateOrderResponse createOrder(CreateOrderRequest request) {
        // 1. 校验链类型
        ChainType.validate(request.getChainType());
        // 3. 自动填充收款地址
        String address = request.getAddress();
        if (address == null || address.trim().isEmpty()) {
            address = getAddressByChainType(request.getChainType());
            if (address == null || address.trim().isEmpty()) {
                throw new BizException(ErrorCode.CHAIN_ADDRESS_NOT_CONFIGURED, "未配置该链类型的收款地址");
            }
        }
        // 3. 计算USDT金额（确保精度一致性）
        int usdtScale = payProperties.getUsdtScale();
        BigDecimal latestRate = UsdtRateService.getCachedRate(); // 获取最新USDT汇率
        BigDecimal actualRate = UsdtRateUtils.calcActualRate(request.getUsdtRate(), latestRate);
        
        // 计算基础USDT金额（从CNY转换为USDT）
        BigDecimal baseAmount = request.getAmount().divide(actualRate, usdtScale, RoundingMode.HALF_UP);
        long usdtUnit = ChainType.getUsdtUnit(request.getChainType());
        
        // 计算步进单位（确保是整数）
        BigDecimal usdtAtom = new BigDecimal(payProperties.getUsdtAtom());
        long atomStep = usdtAtom.multiply(BigDecimal.valueOf(usdtUnit)).longValue();
        
        // 计算基础最小单位（确保精度）
        long baseMinUnit = baseAmount.multiply(BigDecimal.valueOf(usdtUnit)).longValue();
        long actualAmountMinUnit = 0L;
        boolean allocated = false;
        for (int i = 0; i < 100; i++) {
            long tryMinUnit = baseMinUnit + i * atomStep;
            if (amountPoolService.isAmountAvailable(address, tryMinUnit)) {
                if (amountPoolService.allocateAmount(address, tryMinUnit)) {
                    actualAmountMinUnit = tryMinUnit;
                    allocated = true;
                    break;
                }
            }
        }
        if (!allocated) {
            throw new BizException(ErrorCode.AMOUNT_POOL_ALLOCATE_FAILED, "该地址附近USDT金额已被占用，请稍后重试");
        }
        
        // 金额池分配成功，开始异常处理保护
        try {
            // 5. 生成tradeNo
            String tradeNo = UUID.randomUUID().toString().replaceAll("-", "");
            // 6. 组装订单
            Order order = new Order();
            order.setTradeNo(tradeNo);
            order.setOrderNo(request.getOrderNo());
            order.setAmount(calculateCnyToMinUnit(request.getAmount())); // CNY分，使用原始CNY金额
            order.setActualAmount(actualAmountMinUnit); // 实际分配的USDT最小单位
            
            // 验证金额精度一致性
            validateAmountPrecision(actualAmountMinUnit, usdtUnit, usdtScale);
            
            // 记录金额计算详情（用于调试）
            log.info("订单金额计算 - 原始CNY: {}, 汇率: {}, 转换USDT: {}, 链最小单位: {}, 实际分配USDT最小单位: {}", 
                    request.getAmount().toPlainString(), actualRate.toPlainString(), 
                    baseAmount.toPlainString(), usdtUnit, actualAmountMinUnit);
            order.setAddress(address);
            order.setChainType(request.getChainType());
            order.setStatus(OrderStatus.PENDING);
            order.setSignature(request.getSignature());
            order.setUsdtRate(request.getUsdtRate());
            order.setNotifyUrl(request.getNotifyUrl());
            order.setRedirectUrl(request.getRedirectUrl());
            order.setTimeout(request.getTimeout() != null ? request.getTimeout() : payProperties.getExpireTime());
            order.setPaymentUrl(appProperties.getUri() + "/api/v1/order/" + tradeNo);
            order.setNotifyCount(0);
            order.setCreateTime(LocalDateTime.now());
            order.setExpireTime(LocalDateTime.now().plusSeconds(order.getTimeout()));
            order.setNotifyStatus(NotifyStatus.PENDING);
            order.setUpdateTime(LocalDateTime.now());
            order.setTradeIsConfirmed(payProperties.getTradeIsConfirmed());
            order.setUsdtAtom(payProperties.getUsdtAtom());

            // 7. 保存订单
            int insertResult = orderMapper.insert(order);
            if (insertResult <= 0) {
                throw new BizException(ErrorCode.SYSTEM_ERROR, "订单保存失败");
            }
            
            // 8. 记录成功指标
            metricsService.recordOrderCreated(request.getChainType(), request.getAmount().toPlainString());
            
            // 9. 返回VO
            CreateOrderResponse vo = new CreateOrderResponse();
            vo.setTradeNo(order.getTradeNo());
            vo.setOrderNo(order.getOrderNo());

            // CNY金额分转元，确保与创建时的计算一致
            BigDecimal amountYuan = calculateCnyFromMinUnit(order.getAmount());
            vo.setAmount(amountYuan);

            // USDT金额最小单位转USDT，确保与分配时的计算一致
            BigDecimal usdt = calculateUsdtFromMinUnit(order.getActualAmount(), usdtUnit, usdtScale);
            vo.setActualAmount(usdt);
            vo.setAddress(order.getAddress());
            vo.setTimeout(order.getTimeout());
            vo.setPaymentUrl(order.getPaymentUrl());
            return vo;

        } catch (Exception e) {
            allocated = false; // 标记需要释放金额池
            // 记录异常
            log.error("订单创建异常，释放金额池 - 地址: {}, 金额: {}", address, actualAmountMinUnit, e);
            metricsService.recordException(e.getClass().getSimpleName(), e.getMessage());
            metricsService.recordOrderCreatedFailed(request.getChainType(), request.getAmount().toPlainString());
            throw e;
        } finally {
            // 如果订单创建失败，释放金额池
            if (!allocated) {
                try {
                    amountPoolService.releaseAmount(address, actualAmountMinUnit);
                    log.info("异常释放金额池 - 地址: {}, 金额: {}", address, actualAmountMinUnit);
                } catch (Exception releaseException) {
                    log.error("异常释放金额池失败 - 地址: {}, 金额: {}", address, actualAmountMinUnit, releaseException);
                }
            }
        }
    }

    /**
     * 取消订单业务
     */
    @Transactional(rollbackFor = Exception.class)
    public CancelOrderResponse cancelOrder(CancelOrderRequest request) {
        // 1. 查找订单
        Order order = orderMapper.selectOne(
            new LambdaQueryWrapper<Order>()
                .eq(Order::getTradeNo, request.getTradeNo())
        );
        if (order == null) {
            throw new BizException(ErrorCode.ORDER_NOT_FOUND, "订单不存在");
        }
        // 2. 校验状态
        if (!OrderStatus.PENDING.equals(order.getStatus())) {
            throw new BizException(ErrorCode.ORDER_CANNOT_CANCEL, "订单不可取消");
        }
        // 3. 更新订单状态
        order.setStatus(OrderStatus.CANCELLED);
        order.setUpdateTime(LocalDateTime.now());
        orderMapper.updateById(order);

        // 4. 释放金额池
        amountPoolService.releaseAmount(order.getAddress(), order.getActualAmount());
        // 5. 返回VO
        CancelOrderResponse vo = new CancelOrderResponse();
        vo.setTradeNo(order.getTradeNo());

        return vo;
    }

    /**
     * 验证金额精度一致性
     */
    private void validateAmountPrecision(long actualAmountMinUnit, long usdtUnit, int usdtScale) {
        // 验证最小单位必须大于0
        if (actualAmountMinUnit <= 0) {
            throw new BizException(ErrorCode.AMOUNT_TOO_SMALL, "usdt金额必须大于0");
        }
        // 验证转换回USDT时的精度必须完全一致
        BigDecimal usdtAmount = calculateUsdtFromMinUnit(actualAmountMinUnit, usdtUnit, usdtScale);
        long reconstructedMinUnit = usdtAmount.multiply(BigDecimal.valueOf(usdtUnit)).longValue();
        if (reconstructedMinUnit != actualAmountMinUnit) {
            throw new BizException(ErrorCode.AMOUNT_PRECISION_ERROR, 
                    String.format("金额精度验证失败：原始=%d, 重构=%d", actualAmountMinUnit, reconstructedMinUnit));
        }
    }

    /**
     * 从最小单位计算USDT金额，确保精度一致性
     */
    private BigDecimal calculateUsdtFromMinUnit(long actualAmountMinUnit, long usdtUnit, int usdtScale) {
        // 使用与分配时相同的计算逻辑
        return BigDecimal.valueOf(actualAmountMinUnit)
                .divide(BigDecimal.valueOf(usdtUnit), usdtScale, RoundingMode.HALF_UP);
    }

    /**
     * 从CNY分计算CNY元，确保精度一致性
     */
    private BigDecimal calculateCnyFromMinUnit(Long amountInCents) {
        // 使用与创建时相同的计算逻辑（HALF_UP）
        return BigDecimal.valueOf(amountInCents)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    /**
     * 从CNY元计算CNY分，确保精度一致性
     */
    private Long calculateCnyToMinUnit(BigDecimal amountInYuan) {
        // 使用与转换时相同的计算逻辑（HALF_UP）
        return amountInYuan.multiply(BigDecimal.valueOf(100)).longValue();
    }
} 