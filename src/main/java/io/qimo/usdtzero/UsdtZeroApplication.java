package io.qimo.usdtzero;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.beans.factory.annotation.Value;

@SpringBootApplication
public class UsdtZeroApplication {

    @Value("${spring.datasource.url}")
    private static String datasourceUrl;

    public static void main(String[] args) {
        SpringApplication.run(UsdtZeroApplication.class, args);
        System.out.println("Database URL: " + datasourceUrl);
    }

}
