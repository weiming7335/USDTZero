# Linux部署指南, Ubuntu 24.04为例子

## 前置条件

### 1. 安装JDK 24

```bash
# 更新系统
sudo apt update

# 添加Eclipse Temurin仓库
wget -O - https://packages.adoptium.net/artifactory/api/gpg/key/public | sudo apt-key add -
echo "deb https://packages.adoptium.net/artifactory/deb $(awk -F= '/^VERSION_CODENAME/{print$2}' /etc/os-release) main" | sudo tee /etc/apt/sources.list.d/adoptium.list
sudo apt update
sudo apt install temurin-24-jdk

# 验证安装
java -version
```

### 2. 设置时钟同步

```bash
# 设置上海时区
sudo timedatectl set-timezone Asia/Shanghai

# 启用systemd-timesyncd（Ubuntu默认时间同步服务）
sudo timedatectl set-ntp true

# 验证时钟同步状态
timedatectl status

# 验证时钟同步
echo "=== 验证当前时间 ==="
date

```

### 3. 安装Maven

```bash
# 安装Maven
sudo apt install maven

# 验证安装
mvn -version
```

### 4. 下载项目

```bash
# 克隆项目
git clone https://github.com/weiming7335/USDTZero.git
cd USDTZero

# 或者下载ZIP包
wget https://github.com/weiming7335/USDTZero/archive/refs/heads/main.zip
unzip main.zip
cd USDTZero-main
```

## 安装部署

### 1. 编译项目

```bash
# 清理并编译
mvn clean package -DskipTests

# 编译成功后会在target目录生成jar文件
ls target/USDTZero-*.jar
```

### 2. 创建配置文件

```bash
# 创建配置目录
mkdir -p /opt/usdtzero/config
mkdir -p /opt/usdtzero/data
mkdir -p /opt/usdtzero/logs

# 复制配置文件
cp src/main/resources/application.yml /opt/usdtzero/config/
cp src/main/resources/application-prod.yml /opt/usdtzero/config/
```

### 3. 创建启动脚本

```bash
# 创建启动脚本
cat > /opt/usdtzero/start.sh << 'EOF'
#!/bin/bash
cd /opt/usdtzero
java -jar USDTZero-v1.0.1.jar --spring.profiles.active=prod --spring.config.location=file:/opt/usdtzero/config/
EOF

# 设置执行权限
chmod +x /opt/usdtzero/start.sh
```

### 4. 复制JAR文件

```bash
# 复制编译好的jar文件
cp target/USDTZero-v1.0.1.jar /opt/usdtzero/
```

### 5. 启动应用

```bash
# 启动应用
cd /opt/usdtzero
./start.sh

# 或者后台运行
nohup ./start.sh > logs/usdtzero.log 2>&1 &
```

## 验证部署

### 1. 检查进程

```bash
# 查看Java进程
ps aux | grep java

# 查看端口占用
netstat -tlnp | grep 23456
```

### 2. 检查日志

```bash
# 查看应用日志
tail -f /opt/usdtzero/logs/usdtzero.log
```


## 系统服务配置（可选）

### 1. 创建systemd服务

```bash
# 创建服务文件
sudo cat > /etc/systemd/system/usdtzero.service << 'EOF'
[Unit]
Description=USDTZero Application
After=network.target

[Service]
Type=simple
WorkingDirectory=/opt/usdtzero
ExecStart=/usr/bin/java -jar USDTZero-v1.0.1.jar --spring.profiles.active=prod --spring.config.location=file:/opt/usdtzero/config/
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
EOF
```

### 2. 服务管理

```bash
# 重新加载systemd
sudo systemctl daemon-reload

# 启用服务（开机自启动）
sudo systemctl enable usdtzero

# 启动服务
sudo systemctl start usdtzero

# 查看状态
sudo systemctl status usdtzero

# 停止服务
sudo systemctl stop usdtzero

# 重启服务
sudo systemctl restart usdtzero

# 重新加载配置（不重启）
sudo systemctl reload usdtzero

# 禁用开机自启动
sudo systemctl disable usdtzero
```
