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

### 2. 拉取镜像

#### 2.1 进入镜像管理
1. 在1Panel左侧菜单选择 **"镜像"**
2. 点击 **"拉取镜像"** 按钮

#### 2.2 配置镜像信息
1. **来源**：选择 "镜像仓库"
2. **仓库名**：选择 "Docker Hub"
3. **镜像名**：输入 `weiming7335/usdtzero:latest`
4. 点击 **"确定"** 开始拉取

#### 2.3 验证镜像
- 等待镜像拉取完成
- 在镜像列表中确认 `weiming7335/usdtzero:latest` 已存在

### 3. 上传配置文件

#### 3.1 进入1Panel文件管理
1. 在1Panel左侧菜单选择 **"文件"**
2. 进入目录：`/opt/1panel/docker/compose/usdtzero/`

#### 3.2 创建config目录
1. 点击 **"新建文件夹"**
2. 文件夹名称：`config`

#### 3.3 上传配置文件
1. 进入 `config` 目录
2. 点击 **"上传文件"**
3. 上传以下文件：
   - `application.yml`
   - `application-prod.yml`

**注意**：配置文件内容可以从项目源码中获取：

### 4. 创建编排

#### 4.1 进入编排管理
1. 在1Panel左侧菜单选择 **"编排"**
2. 点击 **"创建编排"** 按钮

#### 4.2 配置编排信息
1. **来源**：选择 "编辑"
2. **文件夹**：输入 `usdtzero`
3. 配置文件将保存到：`/opt/1panel/docker/compose/usdtzero/`

#### 4.3 编写Docker Compose配置
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

#### 4.4 保存并启动
1. 点击 **"确定"** 保存配置
2. 系统会自动创建编排并启动服务

### 5. 验证部署

#### 5.1 检查服务状态
1. 在编排列表中查看 `usdtzero` 状态
2. 确认状态为"运行中"

#### 5.2 测试应用
```bash
# 检查应用状态
docker ps 

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

### 0. 安装OpenResty（如果需要）
如果1Panel中没有安装OpenResty，需要先安装：

1. 在1Panel左侧菜单选择 **"网站"**
2. 查看OpenResty状态
3. 如果显示"未启动"或"未安装"，点击"安装"按钮
4. 等待安装完成，确保状态显示为"已启动"

### 1. 创建反向代理
1. 在1Panel左侧菜单选择 **"网站"**
2. 点击 **"创建"** 按钮
3. 选择 **"反向代理"** 标签页
4. 填写配置信息：
   - **分组**：选择 "Default"
   - **主域名**：输入你的域名（如：`your-domain.com`）或IP地址（如：`192.168.1.100`）
   - **代号**：输入 `usdtzero`（这将作为网站目录名）
   - **代理地址**：输入 `http://127.0.0.1:23456`



### 2. 验证配置
```bash
# 测试网站访问
curl http://your-domain.com/monitor/health

```

## 配置说明

### 环境变量
- `SPRING_PROFILES_ACTIVE=prod` - 使用生产环境配置
- `TZ=Asia/Shanghai` - 设置时区

### 数据持久化
- 数据库文件：`./data/`
- 日志文件：`./logs/`



## 管理操作

### 编排管理
```bash
# 启动服务
1Panel -> 编排 -> usdtzero -> 启动

# 停止服务
1Panel -> 编排 -> usdtzero -> 停止

# 重启服务
1Panel -> 编排 -> usdtzero -> 重启

# 删除编排
1Panel -> 编排 -> usdtzero -> 删除
```

### 容器管理
```bash
# 查看容器状态
1Panel -> 容器 -> 查看 usdtzero 容器

# 进入容器
1Panel -> 容器 -> usdtzero -> 终端

# 查看日志
1Panel -> 容器 -> usdtzero -> 日志
```

### 镜像管理
```bash
# 更新镜像
1Panel -> 镜像 -> 拉取镜像 -> weiming7335/usdtzero:latest

# 清理旧镜像
1Panel -> 镜像 -> 清理镜像
```

### 数据备份
```bash
# 备份数据目录
cp -r /opt/1panel/docker/compose/usdtzero/data /opt/backup/usdtzero-data-$(date +%Y%m%d)

# 备份日志目录
cp -r /opt/1panel/docker/compose/usdtzero/logs /opt/backup/usdtzero-logs-$(date +%Y%m%d)
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
