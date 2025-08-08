package io.qimo.usdtzero.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;

@Data
@Slf4j
@Component
@ConfigurationProperties(prefix = "app")
public class AppProperties {
    private String uri;
    private String authToken;

    private String staticPath = "static";
    private String sqlitePath;
    @Value("${server.port:8080}")
    private int serverPort;

    @PostConstruct
    public void validate() {
        // 如果uri为空，设置为默认的IP:port格式，使用实际配置的端口
        if (!StringUtils.hasText(uri)) {
            uri = getLocalIpAddress() + ":" + serverPort;
        }
        
        log.info("[AppProperties] uri={}", uri);
        log.info("[AppProperties] authToken={}", authToken);
        log.info("[AppProperties] staticPath={}", staticPath);
        log.info("[AppProperties] sqlitePath={}", sqlitePath);
        log.info("[AppProperties] serverPort={}", serverPort);
        
        // 校验认证Token不能为空
        if (!StringUtils.hasText(authToken)) {
            throw new IllegalArgumentException("认证Token不能为空");
        }
    }
    
    /**
     * 获取本地IP地址
     * @return 本地IP地址，如果获取失败返回localhost
     */
    private String getLocalIpAddress() {
        try {
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
            while (networkInterfaces.hasMoreElements()) {
                NetworkInterface networkInterface = networkInterfaces.nextElement();
                // 跳过回环接口和虚拟接口
                if (networkInterface.isLoopback() || !networkInterface.isUp() || networkInterface.isVirtual()) {
                    continue;
                }
                
                Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    // 只获取IPv4地址
                    if (!address.isLoopbackAddress() && address.getHostAddress().indexOf(':') == -1) {
                        return address.getHostAddress();
                    }
                }
            }
        } catch (SocketException e) {
            log.warn("获取本地IP地址失败，使用localhost", e);
        }
        return "localhost";
    }
} 