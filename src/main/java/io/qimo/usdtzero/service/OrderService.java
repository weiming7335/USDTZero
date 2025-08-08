package io.qimo.usdtzero.service;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
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
import io.qimo.usdtzero.util.RateUtils;
import io.qimo.usdtzero.util.AmountConvertUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import io.qimo.usdtzero.event.OrderPaidEvent;
import io.qimo.usdtzero.event.OrderMessage;
import org.springframework.context.ApplicationEventPublisher;
import io.qimo.usdtzero.event.CallbackNotifyEvent;
import io.qimo.usdtzero.event.CallbackNotifyMessage;
import io.qimo.usdtzero.api.response.OrderDetailResponse;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

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
    @Autowired
    private UsdtRateService usdtRateService;
    @Autowired
    private ApplicationEventPublisher eventPublisher;


    /**
     * 根据链类型获取收款地址，判断链是否启用
     */
    private String getAddressByChainType(String chainType) {
        if (StringUtils.isBlank(chainType)) return "";
        switch (chainType) {
            case ChainType.TRC20:
                if (Boolean.FALSE.equals(chainProperties.getTrc20Enable())) {
                    throw new BizException(ErrorCode.CHAIN_NOT_ENABLED, "TRC20链未启用");
                }
                if (StringUtils.isNotBlank(chainProperties.getTrc20Address())) {
                    return chainProperties.getTrc20Address();
                }
                break;
            case ChainType.SPL:
                if (Boolean.FALSE.equals(chainProperties.getSplEnable())) {
                    throw new BizException(ErrorCode.CHAIN_NOT_ENABLED, "Solana SPL链未启用");
                }
                if (StringUtils.isNotBlank(chainProperties.getSplAddress())) {
                    return chainProperties.getSplAddress();
                }
                break;
            case ChainType.BEP20:
                if (Boolean.FALSE.equals(chainProperties.getBep20Enable())) {
                    throw new BizException(ErrorCode.CHAIN_NOT_ENABLED, "BEP20链未启用");
                }
                if (StringUtils.isNotBlank(chainProperties.getBep20Address())) {
                    return chainProperties.getBep20Address();
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
        int usdtScale = payProperties.getScale();
        BigDecimal actualRate;
        if (StringUtils.isNotBlank(request.getRate())){
            actualRate = new BigDecimal(request.getRate());
        }else{
            BigDecimal latestRate = usdtRateService.getCachedRate(); // 获取最新USDT汇率
            actualRate = RateUtils.calcActualRate(payProperties.getRate(), latestRate);
        }

        
        // 计算基础USDT金额（从CNY转换为USDT）
        BigDecimal baseAmount = request.getAmount().divide(actualRate, usdtScale, RoundingMode.HALF_UP);
        long usdtUnit = ChainType.getUsdtUnit(request.getChainType());
        
        // 计算步进单位（确保是整数）
        BigDecimal usdtAtom = new BigDecimal(payProperties.getAtom());
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

        // 5. 生成tradeNo
        String tradeNo = UUID.randomUUID().toString().replaceAll("-", "");
        // 金额池分配成功，开始异常处理保护
        try {

            LocalDateTime expireTime = LocalDateTime.now().plusSeconds(request.getTimeout() != null ? request.getTimeout() : payProperties.getTimeout());
            
            // 6. 更新资金池中的订单号和过期时间
            boolean updated = amountPoolService.updateOrderTradeNoAndExpireTime(address, actualAmountMinUnit, tradeNo, expireTime);
            if (!updated) {
                throw new BizException(ErrorCode.AMOUNT_POOL_ALLOCATE_FAILED, "资金池订单号关联更新失败");
            }
            
            // 7. 组装订单
            Order order = new Order();
            order.setTradeNo(tradeNo);
            order.setOrderNo(request.getOrderNo());
            order.setAmount(AmountConvertUtils.calculateCnyToMinUnit(request.getAmount())); // CNY分，使用原始CNY金额
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
            order.setRate(String.valueOf(actualRate));
            order.setNotifyUrl(request.getNotifyUrl());
            order.setTimeout(request.getTimeout() != null ? request.getTimeout() : payProperties.getTimeout());
            order.setPaymentUrl(appProperties.getUri() + "/api/v1/order/pay/" + tradeNo);
            order.setNotifyCount(0);
            order.setCreateTime(LocalDateTime.now());
            order.setExpireTime(expireTime);
            order.setNotifyStatus(NotifyStatus.PENDING);
            order.setUpdateTime(LocalDateTime.now());
            order.setTradeIsConfirmed(payProperties.getTradeIsConfirmed());
            order.setScale(payProperties.getScale());

            // 8. 保存订单
            int insertResult = orderMapper.insert(order);
            if (insertResult <= 0) {
                throw new BizException(ErrorCode.SYSTEM_ERROR, "订单保存失败");
            }
            
            // 9. 记录成功指标
            metricsService.recordOrderCreated(order.getTradeNo());
            
            // 10. 返回VO
            CreateOrderResponse vo = new CreateOrderResponse();
            vo.setTradeNo(order.getTradeNo());
            vo.setOrderNo(order.getOrderNo());

            // CNY金额分转元，确保与创建时的计算一致
            BigDecimal amountYuan = AmountConvertUtils.calculateCnyFromMinUnit(order.getAmount());
            vo.setAmount(amountYuan);

            // USDT金额最小单位转USDT，确保与分配时的计算一致
            BigDecimal usdt = AmountConvertUtils.calculateUsdtFromMinUnit(order.getActualAmount(), usdtUnit, usdtScale);
            vo.setActualAmount(usdt);
            vo.setAddress(order.getAddress());
            vo.setTimeout(order.getTimeout());
            vo.setPaymentUrl(order.getPaymentUrl());
            return vo;

        } catch (Exception e) {
            allocated = false; // 标记需要释放金额池
            // 记录异常
            log.error("订单创建异常，释放金额池 - 地址: {}, 金额: {}", address, actualAmountMinUnit, e);
            metricsService.recordOrderCreatedFailed(tradeNo);
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
        
        // 2. 使用状态条件更新，只有当订单状态为PENDING时才能取消
        int updateResult = orderMapper.updateStatusIfMatch(
            order.getId(), 
            OrderStatus.PENDING, 
            OrderStatus.CANCELLED
        );
        
        if (updateResult == 0) {
            throw new BizException(ErrorCode.ORDER_CANNOT_CANCEL, "订单状态已变更，无法取消");
        }

        // 3. 释放金额池
        amountPoolService.releaseAmount(order.getAddress(), order.getActualAmount());
        // 埋点统计 - 订单取消
        metricsService.recordOrderCancelled(order.getTradeNo());
        // 4. 返回VO
        CancelOrderResponse vo = new CancelOrderResponse();
        vo.setTradeNo(order.getTradeNo());

        return vo;
    }

    /**
     * 匹配到链上支付后，标记订单为已支付
     * @param address 收款地址
     * @param actualAmount  实际到账最小单位
     */
    @Transactional(rollbackFor = Exception.class)
    public void markOrderAsPaid(String address, long actualAmount, String txHash) {
        AmountPoolService.AmountPoolEntry entry = amountPoolService.getEntryByAddressAndAmount(address, actualAmount);
        if (entry == null) {
            log.warn("金额池无此条目，address={}, actualAmount={}", address, actualAmount);
            return;
        }
        Order order = orderMapper.selectOne(
            new LambdaQueryWrapper<Order>()
                .eq(Order::getTradeNo, entry.getOrderTradeNo())
        );
        if (order == null) {
            log.warn("未找到订单，tradeNo={}, address={}, amount={}", entry.getOrderTradeNo(), address, actualAmount);
            return;
        }
        int updateResult = orderMapper.updateStatusIfMatch(order.getId(), OrderStatus.PENDING, OrderStatus.PAID);
        if (updateResult == 1) {
            // 更新交易哈希
            LocalDateTime payTime = LocalDateTime.now();
            orderMapper.updatePayTimeAndTxHashById(order.getId(), payTime, txHash);
            amountPoolService.releaseAmount(address, actualAmount);
            metricsService.recordPaymentReceived(order.getChainType(),order.getAmount(), actualAmount, order.getTradeNo());
            log.info("订单支付成功，tradeNo={}, address={}, actualAmount={}, txHash={}",
                order.getTradeNo(), address, actualAmount, txHash);
            // 发送支付成功事件通知
            OrderMessage msg = new OrderMessage();
            msg.setTradeNo(order.getTradeNo());
            msg.setToAddress(order.getAddress());
            msg.setAmount(AmountConvertUtils.calculateCnyFromMinUnit(order.getAmount()));
            msg.setActualAmount(AmountConvertUtils.calculateUsdtFromMinUnit(order.getActualAmount(), ChainType.getUsdtUnit(order.getChainType()), order.getScale()));
            msg.setChainType(order.getChainType());
            msg.setTxHash(txHash);
            msg.setCreateTime(order.getCreateTime());
            msg.setPayTime(payTime);
            eventPublisher.publishEvent(new OrderPaidEvent(this, msg));

            // 发送回调通知事件
            CallbackNotifyMessage notifyMessage = new CallbackNotifyMessage(order.getTradeNo(), OrderStatus.PAID);
            eventPublisher.publishEvent(new CallbackNotifyEvent(this, notifyMessage));

        } else {
            log.warn("订单状态更新失败，tradeNo={}, address={}, actualAmount={}", order.getTradeNo(), address, actualAmount);
        }
    }

    /**
     * 更新订单的通知信息（notifyCount、notifyStatus、lastNotifyTime）
     * @param orderId 订单ID
     * @param notifyCount 通知次数
     * @param notifyStatus 通知状态（NotifyStatus 常量）
     * @param lastNotifyTime 最后通知时间
     */
    public void updateOrderNotifyInfo(Long orderId, Integer notifyCount, String notifyStatus, LocalDateTime lastNotifyTime) {
        LambdaUpdateWrapper<Order> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(Order::getId, orderId)
                .set(Order::getNotifyCount, notifyCount)
                .set(Order::getNotifyStatus, notifyStatus)
                .set(Order::getLastNotifyTime, lastNotifyTime)
                .set(Order::getUpdateTime, LocalDateTime.now());
        orderMapper.update(updateWrapper);
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
        BigDecimal usdtAmount = AmountConvertUtils.calculateUsdtFromMinUnit(actualAmountMinUnit, usdtUnit, usdtScale);
        long reconstructedMinUnit = usdtAmount.multiply(BigDecimal.valueOf(usdtUnit)).longValue();
        if (reconstructedMinUnit != actualAmountMinUnit) {
            throw new BizException(ErrorCode.AMOUNT_PRECISION_ERROR, 
                    String.format("金额精度验证失败：原始=%d, 重构=%d", actualAmountMinUnit, reconstructedMinUnit));
        }
    }

    /**
     * 处理单个超时订单
     */
    @Transactional(rollbackFor = Exception.class)
    public void processTimeoutOrder(Order order) {
        try {
            log.info("处理超时订单: {}", order.getTradeNo());
            // 1. 只有当订单状态仍为PENDING时才更新为EXPIRED
            int updateResult = orderMapper.updateStatusIfMatch(
                order.getId(),
                OrderStatus.PENDING,
                OrderStatus.EXPIRED
            );
            if (updateResult == 0) {
                log.warn("订单 {} 状态已变更，跳过超时处理", order.getTradeNo());
                return;
            }

            this.updateOrderNotifyInfo(order.getId(),  0,NotifyStatus.PENDING,null);

            // 2. 释放资金池
            if (order.getAddress() != null && order.getActualAmount() != null) {
                amountPoolService.releaseAmount(order.getAddress(), order.getActualAmount());
                log.info("超时订单 {} 资金池释放成功", order.getTradeNo());
            }
            // 发送回调通知事件
            CallbackNotifyMessage notifyMessage = new CallbackNotifyMessage(order.getTradeNo(), OrderStatus.EXPIRED);
            eventPublisher.publishEvent(new CallbackNotifyEvent(this, notifyMessage));
        } catch (Exception e) {
            log.error("处理超时订单 {} 时发生错误: {}", order.getTradeNo(), e.getMessage());
        }
    }

    /**
     * 根据 tradeNo 获取订单
     */
    public Order getByTradeNo(String tradeNo) {
        return orderMapper.selectOne(new LambdaQueryWrapper<Order>()
                .eq(Order::getTradeNo, tradeNo));
    }

    public Order getByTxHash(String txHash){
        return orderMapper.selectOne(new LambdaQueryWrapper<Order>()
                .eq(Order::getTxHash, txHash));
    }

    /**
     * 根据tradeNo查询订单详情，返回OrderDetailResponse
     */
    public OrderDetailResponse getOrderDetailByTradeNo(String tradeNo) {
        Order order = getByTradeNo(tradeNo);
        if (order == null) {
            throw new BizException(ErrorCode.ORDER_NOT_FOUND, "订单不存在");
        }
        OrderDetailResponse resp = new OrderDetailResponse();
        resp.setTradeNo(order.getTradeNo());
        resp.setOrderNo(order.getOrderNo());
        resp.setAmount(AmountConvertUtils.calculateCnyFromMinUnit(order.getAmount()));
        resp.setActualAmount(AmountConvertUtils.calculateUsdtFromMinUnit(order.getActualAmount(), ChainType.getUsdtUnit(order.getChainType()), order.getScale()));
        resp.setAddress(order.getAddress());
        if (order.getExpireTime() != null) {
            long seconds = Duration.between(LocalDateTime.now(), order.getExpireTime()).getSeconds();
            resp.setTimeout((int)Math.max(0, seconds));
        } else {
            resp.setTimeout(0);
        }
        resp.setChainType(order.getChainType());
        resp.setStatus(order.getStatus());
        return resp;
    }
} 