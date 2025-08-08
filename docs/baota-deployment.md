# USDTZero 宝塔容器部署指南

## 前置条件

- 已安装宝塔面板
- 已安装Docker管理器插件
- 服务器已配置好网络和防火墙
- 确保服务器时间同步

### 宿主机时间同步设置

```bash
# 设置上海时区
timedatectl set-timezone Asia/Shanghai

# 启用时间同步
timedatectl set-ntp true

# 验证时间同步状态
timedatectl status

# 检查当前时间
date
```

## 部署步骤

### 1. 登录宝塔面板

### 2. 安装Docker管理器

#### 2.1 安装Docker插件
1. 在宝塔面板左侧菜单选择 **"软件商店"**
2. 搜索 **"Docker管理器"**
3. 点击 **"安装"** 按钮
4. 等待安装完成

#### 2.2 验证Docker安装
1. 在宝塔面板左侧菜单选择 **"Docker"**
2. 确认Docker服务状态为"运行中"

### 3. 拉取镜像

#### 3.1 进入Docker管理
1. 在宝塔面板左侧菜单选择 **"Docker"**
2. 点击 **"镜像管理"** 标签页

#### 3.2 拉取应用镜像
1. 点击 **"拉取镜像"** 按钮
2. 输入镜像名称：`weiming7335/usdtzero:latest`
3. 点击 **"确定"** 开始拉取
4. 等待镜像拉取完成

### 4. 上传配置文件

#### 4.1 进入文件管理
1. 在宝塔面板左侧菜单选择 **"文件"**
2. 进入目录：`/www/server/panel/data/compose/usdtzero/`

#### 4.2 创建config目录
1. 点击 **"新建文件夹"**
2. 文件夹名称：`config`

#### 4.3 上传配置文件
1. 进入 `config` 目录
2. 点击 **"上传文件"**
3. 上传以下文件：
   - `application.yml`
   - `application-prod.yml`

**注意**：配置文件内容可以从项目源码中获取。

### 5. 创建编排（推荐方式）

#### 5.1 进入编排管理
1. 在宝塔面板左侧菜单选择 **"Docker"**
2. 点击 **"编排管理"** 标签页
3. 点击 **"添加编排"** 按钮

#### 5.2 配置编排信息
1. **编排名称**：`usdtzero`
2. **编排目录**：`/www/server/panel/data/compose/usdtzero`

#### 5.3 编写Docker Compose配置
在编辑器中输入以下内容：

```yaml
version: '3.8'

services:
  usdtzero:
    container_name: usdtzero
    image: weiming7335/usdtzero:latest
    ports:
      - "23456:23456"
    volumes:
      - ./config/application.yml:/app/application.yml
      - ./config/application-prod.yml:/app/application-prod.yml
      - ./data:/app/data
      - ./logs:/app/logs
    environment:
      - SPRING_PROFILES_ACTIVE=prod
      - TZ=Asia/Shanghai
    restart: unless-stopped
```

#### 5.4 创建编排
1. 点击 **"提交"** 按钮
2. 等待编排创建完成
3. 点击 **"启动"** 按钮启动服务

### 7. 验证部署

#### 7.1 检查容器状态
1. 在容器管理页面查看 `usdtzero` 容器状态
2. 确认状态为"运行中"

#### 7.2 测试应用
```bash
# 检查应用状态
docker ps | grep usdtzero

# 测试API接口
curl http://localhost:23456/monitor/health

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

### 1. 安装Nginx

#### 1.1 安装Nginx
1. 在宝塔面板左侧菜单选择 **"软件商店"**
2. 搜索 **"Nginx"**
3. 点击 **"安装"** 按钮
4. 等待安装完成

### 2. 创建网站

#### 2.1 配置反向代理
1. 在站点管理页面点击 **"设置"**
2. 选择 **"反向代理"** 标签页
3. 点击 **"添加反向代理"** 按钮
4. 配置代理信息：
    - **域名**：输入你的域名（如：`your-domain.com`）
   - **目标URL**：`http://127.0.0.1:23456`
   - **发送域名**：`$host`
5. 点击 **"提交"** 保存配置

### 3. 验证配置
```bash
# 测试网站访问
curl http://your-domain.com/monitor/health

```

## 配置说明


### 数据持久化
- 数据库文件：`/www/docker/usdtzero/data/`
- 日志文件：`/www/docker/usdtzero/logs/`


### 数据备份
```bash
# 备份数据目录
cp -r /www/docker/usdtzero/data /www/backup/usdtzero-data-$(date +%Y%m%d)

# 备份日志目录
cp -r /www/docker/usdtzero/logs /www/backup/usdtzero-logs-$(date +%Y%m%d)
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

# 查看容器详细信息
docker inspect usdtzero
```
