package io.qimo.usdtzero.constant;

public class OrderStatus {
    public static final String PENDING = "PENDING";
    public static final String PAID = "PAID";
    public static final String EXPIRED = "EXPIRED";
    public static final String CANCELLED = "CANCELLED"; // 已取消
    public static final String ABNORMAL = "ABNORMAL"; // 异常状态

    /**
     * 根据状态返回中文名称
     * @param status 订单状态
     * @return 中文名称
     */
    public static String getChineseName(String status) {
        if (status == null) {
            return "未知状态";
        }
        
        switch (status) {
            case PENDING:
                return "待支付";
            case PAID:
                return "已支付";
            case EXPIRED:
                return "已过期";
            case CANCELLED:
                return "已取消";
            case ABNORMAL:
                return "异常状态";
            default:
                return "未知状态";
        }
    }
} 