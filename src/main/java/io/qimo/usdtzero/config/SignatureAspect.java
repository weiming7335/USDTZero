package io.qimo.usdtzero.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.qimo.usdtzero.model.BizException;
import io.qimo.usdtzero.model.ErrorCode;
import io.qimo.usdtzero.util.SignUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.HashMap;
import java.util.Map;

/**
 * 签名校验AOP切面
 * 通用的签名校验处理，支持所有需要签名校验的接口
 */
@Aspect
@Component
@Slf4j
public class SignatureAspect {

    @Autowired
    private AppProperties appProperties;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 对所有需要签名校验的接口进行签名验证
     * 使用@SignatureRequired注解标记需要签名校验的方法
     */
    @Around("@annotation(io.qimo.usdtzero.config.SignatureRequired)")
    public Object validateSignature(ProceedingJoinPoint joinPoint) throws Throwable {
        HttpServletRequest request = getCurrentRequest();
        if (request == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "无法获取请求信息");
        }

        // 获取请求体
        String requestBody = getRequestBody(request);
        if (requestBody == null || requestBody.trim().isEmpty()) {
            throw new BizException(ErrorCode.REQUEST_BODY_EMPTY);
        }

        // 解析请求体为Map
        Map<String, Object> requestMap = parseRequestBody(requestBody);
        if (requestMap == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "请求体格式错误");
        }

        // 提取签名
        String signature = extractSignature(requestMap);
        if (signature == null || signature.trim().isEmpty()) {
            throw new BizException(ErrorCode.SIGNATURE_MISSING);
        }

        // 移除签名字段，构建签名参数
        requestMap.remove("signature");
        Map<String, String> paramsForSignature = convertToStringMap(requestMap);

        // 生成期望签名
        String expectedSignature = SignUtils.generateSignature(paramsForSignature, appProperties.getAuthToken());

        // 验证签名
        if (!expectedSignature.equals(signature)) {
            log.warn("签名验证失败 - 期望: {}, 实际: {}, 参数: {}", expectedSignature, signature, paramsForSignature);
            throw new BizException(ErrorCode.SIGNATURE_INVALID);
        }

        log.debug("签名验证通过 - 签名: {}", signature);
        
        // 继续执行原方法
        return joinPoint.proceed();
    }

    /**
     * 获取当前请求
     */
    private HttpServletRequest getCurrentRequest() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes != null ? attributes.getRequest() : null;
    }

    /**
     * 获取请求体内容
     */
    private String getRequestBody(HttpServletRequest request) {
        try {
            // 尝试从ContentCachingRequestWrapper获取缓存的内容
            if (request instanceof org.springframework.web.util.ContentCachingRequestWrapper) {
                org.springframework.web.util.ContentCachingRequestWrapper wrapper = 
                    (org.springframework.web.util.ContentCachingRequestWrapper) request;
                byte[] content = wrapper.getContentAsByteArray();
                if (content != null && content.length > 0) {
                    return new String(content, wrapper.getCharacterEncoding());
                }
            }
            
            // 如果无法获取缓存内容，返回null（这种情况应该不会发生，因为过滤器已经处理了）
            return null;
        } catch (Exception e) {
            log.error("获取请求体失败", e);
            return null;
        }
    }

    /**
     * 解析请求体为Map
     */
    private Map<String, Object> parseRequestBody(String requestBody) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = objectMapper.readValue(requestBody, Map.class);
            return map;
        } catch (Exception e) {
            log.error("解析请求体失败: {}", requestBody, e);
            return null;
        }
    }

    /**
     * 提取签名字段
     */
    private String extractSignature(Map<String, Object> requestMap) {
        Object signatureObj = requestMap.get("signature");
        return signatureObj != null ? signatureObj.toString() : null;
    }

    /**
     * 将Map转换为String类型的Map（用于签名计算）
     */
    private Map<String, String> convertToStringMap(Map<String, Object> map) {
        Map<String, String> result = new HashMap<>();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (entry.getValue() != null) {
                result.put(entry.getKey(), entry.getValue().toString());
            }
        }
        return result;
    }
} 