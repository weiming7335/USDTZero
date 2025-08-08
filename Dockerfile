FROM eclipse-temurin:24-jdk

WORKDIR /app

# 复制 jar 包
COPY target/USDTZero-v1.0.1.jar app.jar

# 复制配置文件
COPY src/main/resources/application.yml application.yml
COPY src/main/resources/application-prod.yml application-prod.yml

EXPOSE 23456

CMD ["java", "-jar", "app.jar"] 