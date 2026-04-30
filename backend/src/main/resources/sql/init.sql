-- 初始化数据库方案: SmartTrade (支持 T+1)
-- 字符集: utf8mb4 / utf8mb4_general_ci

CREATE DATABASE IF NOT EXISTS `smart_trade` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE `smart_trade`;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS `zidatrade_user_asset_snapshot`;
DROP TABLE IF EXISTS `zidatrade_trade_deal`;
DROP TABLE IF EXISTS `zidatrade_ai_predict_log`;
DROP TABLE IF EXISTS `zidatrade_user_position`;
DROP TABLE IF EXISTS `zidatrade_trade_order`;
DROP TABLE IF EXISTS `zidatrade_stock_daily_price`;
DROP TABLE IF EXISTS `zidatrade_stock_info`;
DROP TABLE IF EXISTS `zidatrade_user`;

-- ----------------------------
-- 1. 用户基础表 `zidatrade_user`
-- ----------------------------
CREATE TABLE `zidatrade_user` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `username` varchar(50) NOT NULL COMMENT '登录账号(手机或用户名)',
  `password` varchar(100) NOT NULL COMMENT '密码哈希值',
  `nickname` varchar(50) DEFAULT NULL COMMENT '用户昵称',
  `avatar` varchar(255) DEFAULT NULL COMMENT '头像地址',
  `role` varchar(20) NOT NULL DEFAULT 'USER' COMMENT '用户角色: USER, ADMIN',
  `risk_level` varchar(20) NOT NULL DEFAULT 'BALANCED' COMMENT '风险偏好: CONSERVATIVE/BALANCED/AGGRESSIVE',
  `total_assets` decimal(15, 2) NOT NULL DEFAULT 1000000.00 COMMENT '总资产 = 可用资金 + 持仓市值 + 冻结资金',
  `available_funds` decimal(15, 2) NOT NULL DEFAULT 1000000.00 COMMENT '可用资金',
  `frozen_funds` decimal(15, 2) NOT NULL DEFAULT 0.00 COMMENT '冻结资金',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态(1:正常, 0:禁用)',
  `last_login_at` datetime DEFAULT NULL COMMENT '最近登录时间',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_zidatrade_user_username` (`username`) USING BTREE,
  KEY `idx_zidatrade_user_status` (`status`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户基础信息表';

-- ----------------------------
-- 2. 股票基础信息表 `zidatrade_stock_info`
-- ----------------------------
CREATE TABLE `zidatrade_stock_info` (
  `stock_code` varchar(20) NOT NULL COMMENT '股票代码(如: 600519)',
  `market` varchar(10) NOT NULL COMMENT '市场标识(SH/SZ/HK/US)',
  `stock_name` varchar(50) NOT NULL COMMENT '股票名称',
  `plate_type` varchar(20) DEFAULT NULL COMMENT '板块类型',
  `industry_name` varchar(50) DEFAULT NULL COMMENT '所属行业',
  `listing_date` date DEFAULT NULL COMMENT '上市日期',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '交易状态: 1-正常 0-停牌',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`stock_code`) USING BTREE,
  KEY `idx_zidatrade_stock_market` (`market`) USING BTREE,
  KEY `idx_zidatrade_stock_status` (`status`) USING BTREE,
  KEY `idx_zidatrade_stock_industry` (`industry_name`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='全市场股票基础信息表';

-- ----------------------------
-- 3. 股票日线行情表 `zidatrade_stock_daily_price`
-- ----------------------------
CREATE TABLE `zidatrade_stock_daily_price` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `stock_code` varchar(20) NOT NULL COMMENT '股票代码',
  `trade_date` date NOT NULL COMMENT '交易日期',
  `open_price` decimal(10, 2) DEFAULT NULL COMMENT '开盘价',
  `close_price` decimal(10, 2) DEFAULT NULL COMMENT '收盘价',
  `high_price` decimal(10, 2) DEFAULT NULL COMMENT '最高价',
  `low_price` decimal(10, 2) DEFAULT NULL COMMENT '最低价',
  `pre_close_price` decimal(10, 2) DEFAULT NULL COMMENT '前收价',
  `volume` bigint DEFAULT 0 COMMENT '成交量(股)',
  `turnover_amount` decimal(18, 2) DEFAULT 0.00 COMMENT '成交额',
  `amplitude` decimal(8, 4) DEFAULT NULL COMMENT '振幅',
  `change_percent` decimal(8, 4) DEFAULT NULL COMMENT '涨跌幅',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_zidatrade_daily_stock_date` (`stock_code`, `trade_date`) USING BTREE,
  KEY `idx_zidatrade_daily_trade_date` (`trade_date`) USING BTREE,
  CONSTRAINT `fk_zidatrade_daily_stock_code` FOREIGN KEY (`stock_code`) REFERENCES `zidatrade_stock_info` (`stock_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='股票日线行情表';

-- ----------------------------
-- 4. 交易委托单表 `zidatrade_trade_order`
-- ----------------------------
CREATE TABLE `zidatrade_trade_order` (
  `order_no` varchar(32) NOT NULL COMMENT '订单流水号',
  `user_id` bigint NOT NULL COMMENT '委托用户ID',
  `stock_code` varchar(20) NOT NULL COMMENT '股票代码',
  `direction` varchar(10) NOT NULL COMMENT '方向: BUY/SELL',
  `order_type` varchar(10) NOT NULL COMMENT '类型: LIMIT/MARKET',
  `entrust_price` decimal(10, 2) NOT NULL COMMENT '委托价格',
  `entrust_quantity` int NOT NULL COMMENT '委托数量(股)',
  `match_price` decimal(10, 2) DEFAULT NULL COMMENT '最终成交均价',
  `match_quantity` int NOT NULL DEFAULT 0 COMMENT '已成交数量',
  `frozen_amount` decimal(15, 2) NOT NULL DEFAULT 0.00 COMMENT '冻结金额(买单使用)',
  `turnover_amount` decimal(15, 2) NOT NULL DEFAULT 0.00 COMMENT '累计成交金额',
  `order_status` varchar(15) NOT NULL DEFAULT 'PENDING' COMMENT '状态: PENDING/PARTIAL/FILLED/CANCELED',
  `order_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '委托时间',
  `match_time` datetime DEFAULT NULL COMMENT '最后成交时间',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注信息',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`order_no`) USING BTREE,
  KEY `idx_zidatrade_order_user` (`user_id`) USING BTREE,
  KEY `idx_zidatrade_order_stock` (`stock_code`) USING BTREE,
  KEY `idx_zidatrade_order_status` (`order_status`) USING BTREE,
  KEY `idx_zidatrade_order_time` (`order_time`) USING BTREE,
  CONSTRAINT `fk_zidatrade_order_stock_code` FOREIGN KEY (`stock_code`) REFERENCES `zidatrade_stock_info` (`stock_code`),
  CONSTRAINT `fk_zidatrade_order_user_id` FOREIGN KEY (`user_id`) REFERENCES `zidatrade_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户交易委托单表';

-- ----------------------------
-- 5. 用户持仓表 `zidatrade_user_position`
-- ----------------------------
CREATE TABLE `zidatrade_user_position` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '持仓主键ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `stock_code` varchar(20) NOT NULL COMMENT '股票代码',
  `quantity` int NOT NULL DEFAULT 0 COMMENT '总持仓数量',
  `available_quantity` int NOT NULL DEFAULT 0 COMMENT '可卖持仓数量',
  `frozen_quantity` int NOT NULL DEFAULT 0 COMMENT '冻结持仓数量',
  `cost_price` decimal(10, 2) NOT NULL DEFAULT 0.00 COMMENT '持仓成本价',
  `latest_price` decimal(10, 2) DEFAULT NULL COMMENT '最新价',
  `market_value` decimal(15, 2) NOT NULL DEFAULT 0.00 COMMENT '持仓市值',
  `floating_profit` decimal(15, 2) NOT NULL DEFAULT 0.00 COMMENT '浮动盈亏',
  `last_trade_date` date DEFAULT NULL COMMENT '最近交易日期',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_zidatrade_position_user_stock` (`user_id`, `stock_code`) USING BTREE,
  KEY `idx_zidatrade_position_stock` (`stock_code`) USING BTREE,
  CONSTRAINT `fk_zidatrade_position_stock_code` FOREIGN KEY (`stock_code`) REFERENCES `zidatrade_stock_info` (`stock_code`),
  CONSTRAINT `fk_zidatrade_position_user_id` FOREIGN KEY (`user_id`) REFERENCES `zidatrade_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户持仓快照表';

-- ----------------------------
-- 6. AI 预测日志表 `zidatrade_ai_predict_log`
-- ----------------------------
CREATE TABLE `zidatrade_ai_predict_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `stock_code` varchar(20) NOT NULL COMMENT '预测股票代码',
  `target_date` date NOT NULL COMMENT '目标预测日期',
  `predict_price` decimal(10, 2) NOT NULL COMMENT '预测收盘价',
  `predict_high_price` decimal(10, 2) DEFAULT NULL COMMENT '预测最高价',
  `predict_low_price` decimal(10, 2) DEFAULT NULL COMMENT '预测最低价',
  `confidence_score` decimal(5, 2) DEFAULT NULL COMMENT '预测置信度(0-100)',
  `predict_signal` varchar(20) DEFAULT NULL COMMENT '预测信号: BUY/HOLD/SELL',
  `actual_price` decimal(10, 2) DEFAULT NULL COMMENT '实际收盘价',
  `is_verified` tinyint NOT NULL DEFAULT 0 COMMENT '是否已回填验证',
  `model_version` varchar(30) NOT NULL DEFAULT 'LSTM_V1' COMMENT '模型版本标识',
  `source_type` varchar(20) NOT NULL DEFAULT 'AI' COMMENT '来源类型: AI/MANUAL',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_zidatrade_predict_unique` (`stock_code`, `target_date`, `model_version`) USING BTREE,
  KEY `idx_zidatrade_predict_target_date` (`target_date`) USING BTREE,
  KEY `idx_zidatrade_predict_signal` (`predict_signal`) USING BTREE,
  CONSTRAINT `fk_zidatrade_predict_stock_code` FOREIGN KEY (`stock_code`) REFERENCES `zidatrade_stock_info` (`stock_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='AI 股票预测日志表';

-- ----------------------------
-- 7. 成交明细表 `zidatrade_trade_deal`
-- ----------------------------
CREATE TABLE `zidatrade_trade_deal` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `order_no` varchar(32) NOT NULL COMMENT '关联订单号',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `stock_code` varchar(20) NOT NULL COMMENT '股票代码',
  `deal_price` decimal(10, 2) NOT NULL COMMENT '成交价',
  `deal_quantity` int NOT NULL COMMENT '成交数量',
  `deal_amount` decimal(15, 2) NOT NULL COMMENT '成交金额',
  `deal_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '成交时间',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_zidatrade_deal_order_no` (`order_no`) USING BTREE,
  KEY `idx_zidatrade_deal_user_stock` (`user_id`, `stock_code`) USING BTREE,
  CONSTRAINT `fk_zidatrade_deal_order_no` FOREIGN KEY (`order_no`) REFERENCES `zidatrade_trade_order` (`order_no`),
  CONSTRAINT `fk_zidatrade_deal_stock_code` FOREIGN KEY (`stock_code`) REFERENCES `zidatrade_stock_info` (`stock_code`),
  CONSTRAINT `fk_zidatrade_deal_user_id` FOREIGN KEY (`user_id`) REFERENCES `zidatrade_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='订单成交明细表';

-- ----------------------------
-- 8. 用户资产快照表 `zidatrade_user_asset_snapshot`
-- ----------------------------
CREATE TABLE `zidatrade_user_asset_snapshot` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `snapshot_date` date NOT NULL COMMENT '快照日期',
  `total_assets` decimal(15, 2) NOT NULL DEFAULT 0.00 COMMENT '总资产',
  `available_funds` decimal(15, 2) NOT NULL DEFAULT 0.00 COMMENT '可用资金',
  `market_value` decimal(15, 2) NOT NULL DEFAULT 0.00 COMMENT '持仓市值',
  `position_profit` decimal(15, 2) NOT NULL DEFAULT 0.00 COMMENT '持仓浮盈亏',
  `daily_profit` decimal(15, 2) NOT NULL DEFAULT 0.00 COMMENT '当日收益',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_zidatrade_snapshot_user_date` (`user_id`, `snapshot_date`) USING BTREE,
  KEY `idx_zidatrade_snapshot_date` (`snapshot_date`) USING BTREE,
  CONSTRAINT `fk_zidatrade_snapshot_user_id` FOREIGN KEY (`user_id`) REFERENCES `zidatrade_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户资产每日快照表';

-- ----------------------------
-- 9. 用户自选股表 `zidatrade_user_watchlist`
-- ----------------------------
CREATE TABLE IF NOT EXISTS `zidatrade_user_watchlist` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `stock_code` varchar(20) NOT NULL COMMENT '股票代码',
  `sort_order` int NOT NULL DEFAULT 0 COMMENT '排序值，越小越靠前',
  `remark` varchar(100) DEFAULT NULL COMMENT '备注（可选）',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_zidatrade_watchlist_user_stock` (`user_id`, `stock_code`) USING BTREE,
  KEY `idx_zidatrade_watchlist_user` (`user_id`, `sort_order`) USING BTREE,
  CONSTRAINT `fk_zidatrade_watchlist_user_id` FOREIGN KEY (`user_id`) REFERENCES `zidatrade_user` (`id`),
  CONSTRAINT `fk_zidatrade_watchlist_stock_code` FOREIGN KEY (`stock_code`) REFERENCES `zidatrade_stock_info` (`stock_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户自选股收藏夹';
-- 注意：上面的 COLLATE 必须和 zidatrade_stock_info 保持一致，否则外键会因 collate 不匹配建立失败

SET FOREIGN_KEY_CHECKS = 1;