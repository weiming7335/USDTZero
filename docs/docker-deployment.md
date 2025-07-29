# USDTZero Docker 部署指南

## 项目简介

USDTZero 是一个多链 USDT 支付网关。

## 完整部署流程

### 前置条件
- 已安装 Docker 和 Docker Compose

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
# 使用 Docker 构建（推荐）
docker run --rm -v $(pwd):/app -w /app maven:3.9-openjdk-24 mvn clean package

# 或者本地安装 Maven 后构建
# mvn clean package
```

4. **启动服务**
```bash
# 构建并启动
docker-compose up -d

# 查看运行状态
docker-compose ps

# 查看日志
docker-compose logs -f
```

5. **验证部署**
```bash
# 检查服务是否正常
curl http://localhost:23456/health
```

## 配置说明

项目使用 Spring Profiles 管理不同环境的配置：

- `application.yml` - 基础配置
- `application-prod.yml` - 生产环境配置

Docker 部署会自动使用 `prod` profile，加载 `application-prod.yml` 配置。

### 目录挂载说明

- `./data:/app/data` - 数据库文件目录
- `./logs:/app/logs` - 日志文件目录
- `./src/main/resources/application*.yml:/app/` - 配置文件

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
mvn clean package
docker-compose build
docker-compose up -d
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

## 注意事项

1. 确保 `application-prod.yml` 中的私钥配置正确
2. 生产环境建议使用 HTTPS
3. 定期备份数据库文件
4. 监控服务运行状态

---
