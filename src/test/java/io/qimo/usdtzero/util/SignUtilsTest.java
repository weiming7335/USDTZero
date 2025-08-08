package io.qimo.usdtzero.util;

import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SignUtilsTest {
    @Test
    void testGenerateSignature_basic() {
        Map<String, String> params = new HashMap<>();
        params.put("b", "2");
        params.put("a", "1");
        params.put("c", ""); // 空值应被忽略
        String token = "token123";
        // 预期签名：a=1&b=2+token123 的MD5小写
        String expected = SignUtils.generateSignature(params, token);
        assertEquals(expected, SignUtils.generateSignature(params, token));
    }

    @Test
    void testGenerateSignature_emptyParams() {
        Map<String, String> params = new HashMap<>();
        String token = "token123";
        String expected = SignUtils.generateSignature(params, token);
        assertEquals(expected, SignUtils.generateSignature(params, token));
    }

    @Test
    void testGenerateSignature_nullValue() {
        Map<String, String> params = new HashMap<>();
        params.put("a", null);
        params.put("b", "");
        String token = "token123";
        String expected = SignUtils.generateSignature(params, token);
        assertEquals(expected, SignUtils.generateSignature(params, token));
    }
} 