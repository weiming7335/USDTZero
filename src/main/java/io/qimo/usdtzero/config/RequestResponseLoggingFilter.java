package io.qimo.usdtzero.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import io.qimo.usdtzero.service.LightweightMetricsService;
import org.springframework.beans.factory.annotation.Autowired;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestResponseLoggingFilter extends OncePerRequestFilter {

    @Autowired
    private LightweightMetricsService metricsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(request);
        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);
        
        long startTime = System.currentTimeMillis();
        Exception filterException = null;
        
        try {
            filterChain.doFilter(wrappedRequest, wrappedResponse);
        } catch (Exception ex) {
            filterException = ex;
            throw ex;
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            
            // 记录请求日志
            logRequest(wrappedRequest);
            
            // 记录响应日志
            logResponse(wrappedResponse, duration);
            
            // 埋点统计
            recordMetrics(wrappedRequest, wrappedResponse, duration, filterException);
            
            // 确保响应内容被写入
            wrappedResponse.copyBodyToResponse();
        }
    }

    private void logRequest(ContentCachingRequestWrapper request) {
        try {
            String requestBody = "";
            byte[] requestBodyBytes = request.getContentAsByteArray();
            if (requestBodyBytes.length > 0) {
                String encoding = request.getCharacterEncoding();
                if (encoding == null || !encoding.equalsIgnoreCase("UTF-8")) {
                    encoding = "UTF-8";
                }
                requestBody = new String(requestBodyBytes, encoding);
            }
            
            log.info("[API日志] 请求 - URI: {}, 方法: {}, 参数: {}, 请求体: {}", 
                    request.getRequestURI(), 
                    request.getMethod(), 
                    request.getParameterMap(), 
                    requestBody);
        } catch (Exception e) {
            log.warn("记录请求日志失败", e);
        }
    }

    private void logResponse(ContentCachingResponseWrapper response, long duration) {
        try {
            String responseBody = "";
            byte[] responseBodyBytes = response.getContentAsByteArray();
            if (responseBodyBytes.length > 0) {
                String encoding = response.getCharacterEncoding();
                if (encoding == null || !encoding.equalsIgnoreCase("UTF-8")) {
                    encoding = "UTF-8";
                }
                responseBody = new String(responseBodyBytes, encoding);
            }
            
            log.info("[API日志] 响应 - 状态码: {}, 耗时: {}ms, 响应体: {}", 
                    response.getStatus(),
                    duration,
                    responseBody);
        } catch (Exception e) {
            log.warn("记录响应日志失败", e);
        }
    }

    private void recordMetrics(ContentCachingRequestWrapper request, ContentCachingResponseWrapper response, 
                             long duration, Exception filterException) {
        try {
            metricsService.recordApiResponseTime(duration);
            String endpoint = request.getRequestURI();
            boolean success = response.getStatus() < 400;
            metricsService.recordRequest(endpoint, success);
            if (filterException != null) {
                metricsService.recordException(filterException.getClass().getSimpleName(), filterException.getMessage());
            }
        } catch (Exception e) {
            log.warn("埋点统计异常", e);
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String requestURI = request.getRequestURI();
        return !requestURI.startsWith("/api/") && !requestURI.startsWith("/actuator/");
    }
} 