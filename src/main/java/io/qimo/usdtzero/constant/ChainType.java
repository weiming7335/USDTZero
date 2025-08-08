package io.qimo.usdtzero.constant;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class ChainType {
    public static final String TRC20 = "TRC20";
    public static final String SPL = "SPL";
    public static final String BEP20 = "BEP20";
    // 可扩展更多链类型

    // 所有有效的链类型集合
    private static final Set<String> VALID_CHAIN_TYPES = new HashSet<>(Arrays.asList(
            TRC20,  SPL, BEP20
    ));
    
    /**
     * 验证链类型是否有效
     * @param chainType 要验证的链类型
     * @return true 如果链类型有效，false 如果无效
     */
    public static boolean isValid(String chainType) {
        return chainType != null && VALID_CHAIN_TYPES.contains(chainType);
    }
    
    /**
     * 验证链类型是否有效，如果无效则抛出异常
     * @param chainType 要验证的链类型
     * @throws IllegalArgumentException 如果链类型无效
     */
    public static void validate(String chainType) {
        if (!isValid(chainType)) {
            throw new IllegalArgumentException("无效的链类型: " + chainType + 
                    "，支持的链类型: " + String.join(", ", VALID_CHAIN_TYPES));
        }
    }
    
    /**
     * 获取所有有效的链类型
     * @return 所有有效链类型的集合
     */
    public static Set<String> getValidChainTypes() {
        return new HashSet<>(VALID_CHAIN_TYPES);
    }
    
    /**
     * 获取所有有效链类型的字符串表示
     * @return 所有有效链类型的字符串，用逗号分隔
     */
    public static String getValidChainTypesString() {
        return String.join(", ", VALID_CHAIN_TYPES);
    }

    /**
     * 获取指定链的USDT最小单位（如TRC20为1000000，其他链可扩展）
     */
    public static long getUsdtUnit(String chainType) {
        validate(chainType);
        if (TRC20.equalsIgnoreCase(chainType)) {
            return 1_000_000L;
        } else if (SPL.equalsIgnoreCase(chainType)) {
            return 1_000_000L; // Solana主流USDT合约同样6位精度
        } else if (BEP20.equalsIgnoreCase(chainType)) {
            return 1_000_000L; // BSC主流USDT合约18位精度。
        }
        // 其他链类型可在此扩展
        throw new IllegalArgumentException("不支持的链类型: " + chainType);
    }
} 