# USDTZero 1Panel 部署指南

## 前置条件

- 已安装 1Panel 面板
- 服务器已配置好网络和防火墙
- 确保服务器时间同步

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

## 部署步骤

### 1. 登录1Panel

### 2. 创建应用

#### 2.1 进入应用管理
1. 在1Panel左侧菜单选择 **"应用商店"**
2. 点击 **"创建应用"**

#### 2.2 选择应用类型
1. 选择 **"自定义应用"**
2. 应用名称：`usdtzero`
3. 应用类型：`Docker`

### 3. 配置Docker应用

#### 3.1 基础配置
```yaml
应用名称: usdtzero
应用类型: Docker
镜像名称: usdtzero:latest
端口映射: 23456:23456
```

#### 3.2 环境变量配置
```bash
SPRING_PROFILES_ACTIVE=prod
TZ=Asia/Shanghai
```

#### 3.3 数据卷挂载
```bash
# 数据目录
/app/data -> /opt/usdtzero/data

# 日志目录  
/app/logs -> /opt/usdtzero/logs

# 配置文件
/app/application.yml -> /opt/usdtzero/config/application.yml
/app/application-prod.yml -> /opt/usdtzero/config/application-prod.yml
```

### 4. 构建镜像

#### 4.1 上传代码
1. 在1Panel中进入 **"文件管理"**
2. 创建目录：`/opt/usdtzero`
3. 上传项目文件到该目录

#### 4.2 构建Docker镜像
```bash
# 进入项目目录
cd /opt/usdtzero

# 构建项目
docker run --rm -v $(pwd):/app -w /app maven:3.9-eclipse-temurin-24 mvn clean package -DskipTests

# 构建Docker镜像
docker build -t usdtzero:latest .
```

### 5. 启动应用

#### 5.1 在1Panel中启动
1. 回到应用管理页面
2. 点击 **"启动"** 按钮
3. 等待应用启动完成

#### 5.2 验证部署
```bash
# 检查应用状态
docker ps | grep usdtzero

# 测试API接口
curl http://localhost:23456/health

# 查看应用日志
docker logs usdtzero

# 验证时间同步
echo "=== 宿主机时间 ==="
date
timedatectl status

echo "=== 容器时间 ==="
docker exec usdtzero date
```

## 反向代理配置

### 1. 创建网站
1. 在1Panel左侧菜单选择 **"网站"**
2. 点击 **"创建网站"**
3. 填写网站信息：
   - 域名：`your-domain.com`（或使用IP地址）
   - 端口：`80`（HTTP）

### 2. 配置反向代理
1. 在网站管理页面选择 **"反向代理"**
2. 点击 **"添加反向代理"**
3. 配置代理信息：
   - **代理名称**：`usdtzero`
   - **目标地址**：`http://127.0.0.1:23456`
   - **代理路径**：`/`

### 3. 验证配置
```bash
# 测试网站访问
curl http://your-domain.com/monitor/health

```

## 配置说明

### 环境变量
- `SPRING_PROFILES_ACTIVE=prod` - 使用生产环境配置
- `TZ=Asia/Shanghai` - 设置时区

### 数据持久化
- 数据库文件：`/opt/usdtzero/data/`
- 日志文件：`/opt/usdtzero/logs/`
- 配置文件：`/opt/usdtzero/config/`

## 管理操作

### 应用管理
```bash
# 启动应用
1Panel -> 应用管理 -> usdtzero -> 启动

# 停止应用
1Panel -> 应用管理 -> usdtzero -> 停止

# 重启应用
1Panel -> 应用管理 -> usdtzero -> 重启

# 删除应用
1Panel -> 应用管理 -> usdtzero -> 删除
```

### 日志查看
```bash
# 在1Panel中查看日志
1Panel -> 应用管理 -> usdtzero -> 日志

# 或者使用命令行
docker logs usdtzero
docker logs -f usdtzero
```

### 数据备份
```bash
# 备份数据目录
cp -r /opt/usdtzero/data /opt/backup/usdtzero-data-$(date +%Y%m%d)

# 备份配置文件
cp -r /opt/usdtzero/config /opt/backup/usdtzero-config-$(date +%Y%m%d)
```


### 调试命令
```bash
# 进入容器
docker exec -it usdtzero bash

# 查看应用状态
docker stats usdtzero

# 查看网络配置
docker network ls
docker network inspect bridge
```
