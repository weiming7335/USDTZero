package io.qimo.usdtzero.model;

import lombok.Getter;

@Getter
public enum ErrorCode {
    // 成功
    SUCCESS(0, "成功"),
    
    // 参数相关错误 (1001-1099)
    PARAM_ERROR(1001, "参数错误"),
    PARAM_INVALID_FORMAT(1003, "参数格式错误"),
    PARAM_TYPE_ERROR(1004, "参数类型错误"),
    PARAM_VALUE_ERROR(1005, "参数值错误"),
    
    // 签名验证相关错误 (1101-1199)
    SIGNATURE_MISSING(1101, "签名不能为空"),
    SIGNATURE_INVALID(1102, "签名验证失败"),
    SIGNATURE_EXPIRED(1103, "签名已过期"),
    REQUEST_BODY_EMPTY(1104, "请求体不能为空"),
    
    // 订单相关错误 (2001-2099)
    ORDER_NOT_FOUND(2001, "订单不存在"),
    ORDER_EXPIRED(2002, "订单已过期"),
    ORDER_STATUS_ERROR(2003, "订单状态异常"),
    ORDER_CANNOT_CANCEL(2004, "订单不可取消"),
    ORDER_ALREADY_EXISTS(2005, "订单已存在"),
    
    // 金额池相关错误 (2101-2199)
    AMOUNT_POOL_EXHAUSTED(2101, "金额池耗尽"),
    AMOUNT_POOL_ALLOCATE_FAILED(2102, "金额池分配失败"),
    AMOUNT_POOL_RELEASE_FAILED(2103, "金额池释放失败"),
    AMOUNT_POOL_INSUFFICIENT(2104, "金额池余额不足"),
    
    // 链配置相关错误 (2201-2299)
    CHAIN_NOT_ENABLED(2201, "链未启用"),
    CHAIN_ADDRESS_NOT_CONFIGURED(2202, "链地址未配置"),
    CHAIN_TYPE_INVALID(2203, "无效的链类型"),
    CHAIN_RPC_ERROR(2204, "链RPC连接异常"),
    
    // 金额计算相关错误 (2301-2399)
    AMOUNT_TOO_SMALL(2301, "金额过小"),
    AMOUNT_PRECISION_ERROR(2302, "金额精度计算错误"),
    AMOUNT_CONVERSION_ERROR(2303, "金额转换错误"),
    USDT_RATE_INVALID(2304, "USDT汇率无效"),
    
    // 汇率服务相关错误 (3001-3099)
    RATE_SERVICE_UNAVAILABLE(3001, "汇率服务不可用"),
    RATE_CACHE_MISSING(3002, "汇率缓存未找到"),
    RATE_FETCH_FAILED(3003, "汇率获取失败"),
    RATE_PARSE_ERROR(3004, "汇率解析错误"),
    
    // 区块链相关错误 (4001-4099)
    BLOCKCHAIN_ERROR(4001, "区块链监听异常"),
    BLOCKCHAIN_CONNECTION_ERROR(4002, "区块链连接异常"),
    BLOCKCHAIN_TRANSACTION_ERROR(4003, "区块链交易异常"),
    BLOCKCHAIN_CONTRACT_ERROR(4004, "智能合约异常"),

    
    // 通知相关错误 (5001-5099)
    NOTIFY_FAIL(5001, "通知失败"),
    NOTIFY_URL_INVALID(5002, "通知URL无效"),
    NOTIFY_TIMEOUT(5003, "通知超时"),
    NOTIFY_RETRY_EXCEEDED(5004, "通知重试次数超限"),
    
    // 系统级错误 (9001-9999)
    SYSTEM_ERROR(9001, "系统异常"),
    SYSTEM_TIMEOUT(9002, "系统超时"),
    SYSTEM_RESOURCE_EXHAUSTED(9003, "系统资源耗尽"),
    SYSTEM_MAINTENANCE(9004, "系统维护中"),
    SYSTEM_CONFIG_ERROR(9005, "系统配置错误")

    ;


    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}