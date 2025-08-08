package io.qimo.usdtzero.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

public class SignUtils {
    /**
     * 生成签名
     * @param params 参与签名的参数map
     * @param token 认证token
     * @return 32位小写MD5签名
     */
    public static String generateSignature(Map<String, String> params, String token) {
        // 1. 过滤空值参数
        Map<String, String> filtered = new HashMap<>();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (entry.getValue() != null && !entry.getValue().isEmpty()) {
                filtered.put(entry.getKey(), entry.getValue());
            }
        }
        // 2. 按参数名ASCII码升序排序
        List<String> keys = new ArrayList<>(filtered.keySet());
        Collections.sort(keys);
        // 3. 拼接成key1=value1&key2=value2格式
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < keys.size(); i++) {
            String key = keys.get(i);
            sb.append(key).append("=").append(filtered.get(key));
            if (i < keys.size() - 1) {
                sb.append("&");
            }
        }
        // 4. 拼接token
        sb.append(token);
        // 5. MD5加密并转小写
        return md5(sb.toString()).toLowerCase();
    }

    private static String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] bytes = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : bytes) {
                String hexStr = Integer.toHexString(0xff & b);
                if (hexStr.length() == 1) hex.append('0');
                hex.append(hexStr);
            }
            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException("MD5加密失败", e);
        }
    }
} 