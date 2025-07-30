# USDTZero Docker 部署指南

## 完整部署流程

### 安装 Docker (Ubuntu 24.04)

```bash
# 更新系统
sudo apt update

# 安装必要的包
sudo apt install apt-transport-https ca-certificates curl gnupg lsb-release

# 添加Docker官方GPG密钥
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /usr/share/keyrings/docker-archive-keyring.gpg

# 添加Docker仓库
echo "deb [arch=$(dpkg --print-architecture) signed-by=/usr/share/keyrings/docker-archive-keyring.gpg] https://download.docker.com/linux/ubuntu $(lsb_release -cs) stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

# 更新包索引
sudo apt update

# 安装Docker
sudo apt install docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

# 启动Docker服务
sudo systemctl start docker
sudo systemctl enable docker

# 验证安装
sudo docker --version

# 安装 Docker Compose
sudo apt install docker-compose

# 验证 Docker Compose 安装
docker-compose --version

# 配置用户组
sudo usermod -aG docker $USER
newgrp docker
```

### 宿主机时间同步设置

```bash
# 设置上海时区
sudo timedatectl set-timezone Asia/Shanghai

# 启用时间同步
sudo timedatectl set-ntp true

# 验证时间同步状态
timedatectl status

# 检查当前时间
date
```

### 部署步骤

1. **下载代码**
```bash
# 下载到 /opt 目录
cd /opt
git clone https://github.com/weiming7335/USDTZero.git
cd USDTZero
```

2. **配置环境**
```bash
# 编辑生产环境配置文件
vim src/main/resources/application-prod.yml
```

3. **构建项目**
```bash
# 使用 Docker 构建
docker run --rm -v $(pwd):/app -w /app maven:3.9-eclipse-temurin-24 mvn clean package -DskipTests

# 验证构建结果
ls -la target/USDTZero-*.jar
```

4. **启动服务**
```bash
# 构建Docker镜像
docker-compose build

# 启动服务
docker-compose up -d

# 查看运行状态
docker-compose ps

# 查看日志
docker-compose logs -f usdtzero
```

5. **验证部署**
```bash
# 检查容器状态
docker-compose ps

# 进入容器的bash shell
docker exec -it usdtzero bash
```

## 配置说明

项目使用 Spring Profiles 管理不同环境的配置：

- `application.yml` - 基础配置
- `application-prod.yml` - 生产环境配置

Docker 部署会自动使用 `prod` profile，加载 `application-prod.yml` 配置。

### 目录挂载说明

- `./data:/app/data` - 数据库文件目录
- `./logs:/app/logs` - 日志文件目录
- `./src/main/resources/application.yml:/app/application.yml` - 基础配置文件
- `./src/main/resources/application-prod.yml:/app/application-prod.yml` - 生产环境配置文件

### 环境配置

- `SPRING_PROFILES_ACTIVE=prod` - 使用生产环境配置
- `TZ=Asia/Shanghai` - 设置时区为上海时间

## 常用命令

```bash
# 启动服务
docker-compose up -d

# 停止服务
docker-compose down

# 查看日志
docker-compose logs -f usdtzero

# 重启服务
docker-compose restart

# 更新部署
git pull
docker run --rm -v $(pwd):/app -w /app maven:3.9-eclipse-temurin-24 mvn clean package -DskipTests
docker-compose build
docker-compose up -d
```
```

## 数据备份

```bash
# 备份数据库
docker cp usdtzero:/app/data/usdtzero.db ./backup/

# 或者直接复制挂载的文件
cp ./data/usdtzero.db ./backup/

# 恢复数据库
docker cp ./backup/usdtzero.db usdtzero:/app/data/
# 或者直接替换挂载的文件
cp ./backup/usdtzero.db ./data/
docker restart usdtzero
```
