-- 订单表
CREATE TABLE IF NOT EXISTS `trade_order` (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    trade_no TEXT NOT NULL UNIQUE,         -- 业务主键，唯一
    order_no TEXT,                        -- 平台订单号
    amount INTEGER NOT NULL,              -- CNY金额（单位分）
    actual_amount INTEGER,                -- USDT金额（最小单位）
    address TEXT NOT NULL,                -- 收款地址
    chain_type TEXT NOT NULL,             -- 链类型
    status TEXT NOT NULL,                 -- 订单状态
    signature TEXT,                       -- 签名
    rate TEXT,                            -- 汇率策略
    scale INTEGER,                        -- 最小单位
    trade_is_confirmed INTEGER DEFAULT 0, -- 是否确认（0/1）
    notify_url TEXT,                      -- 通知URL
    redirect_url TEXT,                    -- 跳转URL
    timeout INTEGER,                      -- 超时时间（秒）
    payment_url TEXT,                     -- 支付URL
    notify_count INTEGER DEFAULT 0,       -- 通知次数
    create_time DATETIME, -- 创建时间
    expire_time DATETIME,                 -- 过期时间
    pay_time DATETIME,                    -- 支付时间
    tx_hash TEXT,                         -- 交易哈希
    notify_status TEXT,                   -- 通知状态
    update_time DATETIME,                 -- 更新时间
    last_notify_time DATETIME             -- 最后通知时间
);

-- 为tx_hash添加索引
CREATE INDEX IF NOT EXISTS idx_trade_order_tx_hash ON trade_order(tx_hash);

-- 为expire_time添加索引
CREATE INDEX IF NOT EXISTS idx_trade_order_expire_time ON trade_order(expire_time);

-- 为last_notify_time添加索引
CREATE INDEX IF NOT EXISTS idx_trade_order_last_notify_time ON trade_order(last_notify_time); 