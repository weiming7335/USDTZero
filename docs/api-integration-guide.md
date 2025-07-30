# USDTZero API 对接指南


## 认证机制

### 签名算法
所有需要认证的API都需要进行签名验证，签名算法如下：

1. **参数过滤**: 过滤掉值为空的参数
2. **参数排序**: 按参数名ASCII码升序排序
3. **参数拼接**: 拼接成 `key1=value1&key2=value2` 格式
4. **添加密钥**: 在字符串末尾添加你的API密钥
5. **MD5加密**: 对拼接后的字符串进行MD5加密并转为小写

### 签名示例
```javascript
// 参数
const params = {
    "amount": "100.00",
    "chain_type": "TRC20",
    "order_no": "ORDER123456"
};

// 1. 过滤空值并排序
const sortedParams = Object.keys(params)
    .filter(key => params[key] !== null && params[key] !== '')
    .sort()
    .reduce((result, key) => {
        result[key] = params[key];
        return result;
    }, {});

// 2. 拼接参数
const paramString = Object.keys(sortedParams)
    .map(key => `${key}=${sortedParams[key]}`)
    .join('&');

// 3. 添加密钥
const signString = paramString + 'your_api_secret_key';

// 4. MD5加密
const signature = md5(signString).toLowerCase();
```

### Postman测试用例

#### 1. 创建订单接口测试

**请求配置**：
- **Method**: POST
- **URL**: `http://localhost:23456/api/v1/order/create`
- **Headers**: `Content-Type: application/json`

**Pre-request Script**（在Postman的Pre-request Script标签页中添加）：
```javascript
// 请求参数
const params = {
    "chain_type": "TRC20",
    "amount": "10.00",
    "order_no": "ORDER123456",
    "notify_url": "https://localhost:23456/api/callback/notify",
    "timeout": 1200
};

// API密钥（请替换为你的实际密钥）
const token = "your_test_token_here";

// 过滤空值
const filtered = {};
Object.keys(params).forEach(key => {
    if (params[key] !== null && params[key] !== '') {
        filtered[key] = params[key];
    }
});

// 按ASCII码排序并拼接
const sortedKeys = Object.keys(filtered).sort();
const paramString = sortedKeys.map(key => `${key}=${filtered[key]}`).join('&');

// 添加token（直接拼接，不用&连接）
const signString = paramString + token;

// MD5加密
const signature = CryptoJS.MD5(signString).toString();

console.log('签名字符串:', signString);
console.log('签名结果:', signature);

// 设置到环境变量
pm.environment.set('signature', signature);
```

**请求体**：
```json
{
     "chain_type": "TRC20",
    "amount": "10.00",
    "order_no": "ORDER123456",
    "notify_url": "https://localhost:23456/api/callback/notify",
    "timeout": 1200,
    "signature": "{{signature}}"
}
```

#### 2. 取消订单接口测试

**请求配置**：
- **Method**: POST
- **URL**: `http://localhost:23456/api/v1/order/cancel`
- **Headers**: `Content-Type: application/json`

**Pre-request Script**：
```javascript
// 请求参数
const params = {
    "trade_no": "99498a59c69b478fb142dff055096a85"
};

// API密钥
const token = "your_test_token_here";

// 过滤空值
const filtered = {};
Object.keys(params).forEach(key => {
    if (params[key] !== null && params[key] !== '') {
        filtered[key] = params[key];
    }
});

// 排序并拼接
const sortedKeys = Object.keys(filtered).sort();
const paramString = sortedKeys.map(key => `${key}=${filtered[key]}`).join('&');

// 添加token
const signString = paramString + token;

// MD5加密
const signature = CryptoJS.MD5(signString).toString();

console.log('签名字符串:', signString);
console.log('签名结果:', signature);

pm.environment.set('signature', signature);
```

**请求体**：
```json
{
    "trade_no": "99498a59c69b478fb142dff055096a85",
    "signature": "{{signature}}"
}
```

#### 3. 查询订单详情接口测试

**请求配置**：
- **Method**: GET
- **URL**: `http://localhost:23456/api/v1/order/detail/99498a59c69b478fb142dff055096a85`
- **Headers**: 无需特殊设置

**注意**：查询订单详情接口不需要签名验证，可以直接调用。


#### 6. 预期响应

**创建订单成功响应**：
```json
{
    "code": 0,
    "message": "成功",
    "data": {
        "trade_no": "99498a59c69b478fb142dff055096a85",
        "order_no": "ORDER123456",
        "amount": "100.00",
        "actual_amount": "100.00",
        "address": "TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t",
        "timeout": 1800,
        "payment_url": "http://localhost:23456/pay?trade_no=99498a59c69b478fb142dff055096a85"
    }
}
```


## API接口

### 1. 创建订单

**接口地址**: `POST /api/v1/order/create`

**请求参数**:

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| `chain_type` | String | 是 | 链类型，支持：TRC20、SPL、BEP20 |
| `amount` | BigDecimal | 是 | 订单金额，最小0.01 |
| `order_no` | String | 否 | 客户端订单号 |
| `address` | String | 否 | 指定收款地址，不传则使用配置文件的收款地址 |
| `notify_url` | String | 否 | 支付成功回调地址 |
| `timeout` | Integer | 否 | 订单超时时间（秒），默认1200秒 |
| `rate` | String | 否 | 汇率，不传则使用实时汇率 |
| `signature` | String | 是 | 签名 |

**请求示例**:
```json
{
    "chain_type": "TRC20",
    "amount": "100.00",
    "order_no": "ORDER123456",
    "notify_url": "https://your-domain.com/notify",
    "timeout": 1800,
    "signature": "a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6"
}
```

**响应参数**:

| 参数名 | 类型 | 说明 |
|--------|------|------|
| `code` | Integer | 响应码，0表示成功 |
| `message` | String | 响应消息 |
| `data` | Object | 响应数据 |
| `data.trade_no` | String | 系统交易号 |
| `data.order_no` | String | 商户订单号 |
| `data.amount` | BigDecimal | 订单金额 |
| `data.actual_amount` | BigDecimal | 实际支付金额（考虑汇率） |
| `data.address` | String | 收款地址 |
| `data.timeout` | Integer | 超时时间（秒） |
| `data.payment_url` | String | 支付页面URL |

**响应示例**:
```json
{
    "code": 0,
    "message": "成功",
    "data": {
        "trade_no": "99498a59c69b478fb142dff055096a85",
        "order_no": "ORDER123456",
        "amount": "100.00",
        "actual_amount": "100.00",
        "address": "TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t",
        "timeout": 1200,
        "payment_url": "http://your-domain.com/api/v1/order/pay?trade_no=99498a59c69b478fb142dff055096a85"
    }
}
```

### 2. 取消订单

**接口地址**: `POST /api/v1/order/cancel`

**请求参数**:

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| `trade_no` | String | 是 | 系统交易号 |
| `signature` | String | 是 | 签名 |

**请求示例**:
```json
{
    "trade_no": "99498a59c69b478fb142dff055096a85",
    "signature": "a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6"
}
```

**响应参数**:

| 参数名 | 类型 | 说明 |
|--------|------|------|
| `code` | Integer | 响应码，0表示成功 |
| `message` | String | 响应消息 |
| `data` | Object | 响应数据 |
| `data.trade_no` | String | 系统交易号 |
| `data.status` | String | 订单状态 |

**响应示例**:
```json
{
    "code": 0,
    "message": "成功",
    "data": {
        "trade_no": "99498a59c69b478fb142dff055096a85"
    }
}
```

### 3. 查询订单详情

**接口地址**: `GET /api/v1/order/detail/{trade_no}`

**路径参数**:

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| `trade_no` | String | 是 | 系统交易号 |

**请求示例**:
```bash
GET /api/v1/order/detail/99498a59c69b478fb142dff055096a85
```

**响应参数**:

| 参数名 | 类型 | 说明 |
|--------|------|------|
| `code` | Integer | 响应码，0表示成功 |
| `message` | String | 响应消息 |
| `data` | Object | 响应数据 |
| `data.trade_no` | String | 系统交易号 |
| `data.order_no` | String | 商户订单号 |
| `data.amount` | BigDecimal | 订单金额 |
| `data.actual_amount` | BigDecimal | 实际支付金额 |
| `data.address` | String | 收款地址 |
| `data.timeout` | Integer | 超时时间（秒） |
| `data.chainType` | String | 链类型 |
| `data.status` | String | 订单状态 |

**响应示例**:
```json
{
    "code": 0,
    "message": "成功",
    "data": {
        "trade_no": "99498a59c69b478fb142dff055096a85",
        "order_no": "ORDER123456",
        "amount": "100.00",
        "actual_amount": "100.00",
        "address": "TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t",
        "timeout": 1200,
        "chainType": "TRC20",
        "status": "PAID"
    }
}
```

## 数据字典

### 链类型 (chain_type)

| 值 | 说明  |
|----|------|------|
| `TRC20` | TRON网络USDT  |
| `SPL` | Solana网络USDT  |
| `BEP20` | BSC网络USDT  |

### 订单状态 (status)

| 值 | 说明 |
|----|------|
| `PENDING` | 待支付 |
| `PAID` | 已支付 |
| `EXPIRED` | 已过期 |
| `CANCELLED` | 已取消 |
| `ABNORMAL` | 异常状态 |

### 响应码 (code)

| 值 | 说明 |
|----|------|
| `0` | 成功 |
| `1001` | 参数错误 |
| `1003` | 参数格式错误 |
| `1004` | 参数类型错误 |
| `1005` | 参数值错误 |
| `1101` | 签名不能为空 |
| `1102` | 签名验证失败 |
| `1104` | 请求体不能为空 |
| `2001` | 订单不存在 |
| `2004` | 订单不可取消 |
| `2102` | 金额池分配失败 |
| `2201` | 链未启用 |
| `2202` | 链地址未配置 |
| `2301` | 金额过小 |
| `2302` | 金额精度计算错误 |
| `2304` | USDT汇率无效 |
| `3002` | 汇率缓存未找到 |
| `3003` | 汇率获取失败 |
| `9001` | 系统异常 |
