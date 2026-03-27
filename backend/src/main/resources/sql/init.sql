-- 初始化数据库方案: SmartTrade (支持 T+1)
-- 字符集: utf8mb4 / utf8mb4_general_ci

CREATE DATABASE IF NOT EXISTS `smart_trade` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE `smart_trade`;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- 1. 用户基本表 `sys_user`
-- ----------------------------
DROP TABLE IF EXISTS `sys_user`;
CREATE TABLE `sys_user`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `username` varchar(50) NOT NULL COMMENT '登录账号(手机或用户名)',
  `password` varchar(100) NOT NULL COMMENT '密码哈希值',
  `nickname` varchar(50) NULL COMMENT '用户昵称',
  `avatar` varchar(255) NULL COMMENT '头像地址',
  `total_assets` decimal(15, 2) NOT NULL DEFAULT 1000000.00 COMMENT '总资产 = 可用资金 + 持仓市值 + 冻结资金',
  `available_funds` decimal(15, 2) NOT NULL DEFAULT 1000000.00 COMMENT '可用资金(当前可用于购买的资金)',
  `frozen_funds` decimal(15, 2) NOT NULL DEFAULT 0.00 COMMENT '冻结资金(如挂出买单暂未成交的资金)',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态(1:正常, 0:禁用)',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_username`(`username`) USING BTREE
) ENGINE = InnoDB COMMENT = '用户基础信息表';

-- ----------------------------
-- 2. 股票主表 `stock_info`
-- ----------------------------
DROP TABLE IF EXISTS `stock_info`;
CREATE TABLE `stock_info`  (
  `stock_code` varchar(20) NOT NULL COMMENT '股票代码(如: 600519)',
  `market` varchar(10) NOT NULL COMMENT '市场标识(sh, sz, hk, us)',
  `stock_name` varchar(50) NOT NULL COMMENT '股票名称(如: 贵州茅台)',
  `plate_type` varchar(20) NULL COMMENT '板块(主板/创业板/科创板等)',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '交易状态: 1-正常 0-停牌',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '数据创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '数据更新时间',
  PRIMARY KEY (`stock_code`) USING BTREE,
  INDEX `idx_market`(`market`) USING BTREE
) ENGINE = InnoDB COMMENT = '全市场底层股票信息配置表';

-- ----------------------------
-- 3. 交易委托单表 `trade_order`
-- 注: 包含买卖方向以及市价/限价标记
-- 结算是 价格*数量，不纳入印花税和交易佣金
-- ----------------------------
DROP TABLE IF EXISTS `trade_order`;
CREATE TABLE `trade_order`  (
  `order_no` varchar(32) NOT NULL COMMENT '订单流水号(雪花算法或UUID)',
  `user_id` bigint NOT NULL COMMENT '委托用户',
  `stock_code` varchar(20) NOT NULL COMMENT '股票代码',
  `direction` varchar(10) NOT NULL COMMENT '方向: BUY(买入) / SELL(卖出)',
  `order_type` varchar(10) NOT NULL COMMENT '类型: LIMIT(限价单) / MARKET(市价单)',
  `entrust_price` decimal(10, 2) NOT NULL COMMENT '委托价格',
  `entrust_quantity` int NOT NULL COMMENT '委托数量(股)',
  `match_price` decimal(10, 2) NULL COMMENT '最终成交均价',
  `match_quantity` int NOT NULL DEFAULT 0 COMMENT '已成交数量',
  `order_status` varchar(15) NOT NULL DEFAULT 'PENDING' COMMENT '状态: PENDING(待成交), FILLED(已完成), CANCELED(已撤单), PARTIAL(部分成交)',
  `order_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '委托发起时间',
  `match_time` datetime NULL COMMENT '完成撮合时间',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`order_no`) USING BTREE,
  INDEX `idx_user_id`(`user_id`) USING BTREE,
  INDEX `idx_stock_code`(`stock_code`) USING BTREE,
  INDEX `idx_status`(`order_status`) USING BTREE
) ENGINE = InnoDB COMMENT = '用户现货买卖委托单';


-- ----------------------------
-- 4. 账户持仓表 `user_position`
-- 注: T+1 机制通过 quantity 和 available_quantity 的差异体现
-- ----------------------------
DROP TABLE IF EXISTS `user_position`;
CREATE TABLE `user_position`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '持仓流水ID',
  `user_id` bigint NOT NULL COMMENT '持有用户ID',
  `stock_code` varchar(20) NOT NULL COMMENT '持股代码',
  `quantity` int NOT NULL DEFAULT 0 COMMENT '总持仓数量(包含今日买入未解冻的部分)',
  `available_quantity` int NOT NULL DEFAULT 0 COMMENT '可用持仓数量(可挂卖单的数量, 每日系统清分时将 quantity 同步过来)',
  `frozen_quantity` int NOT NULL DEFAULT 0 COMMENT '卖出冻结数(正挂着卖单待撮合的数量)',
  `cost_price` decimal(10, 2) NOT NULL COMMENT '持仓均价建仓成本(每次买入需动态加权计算)',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_user_stock`(`user_id`, `stock_code`) USING BTREE
) ENGINE = InnoDB COMMENT = '用户现货持仓快照表';

-- ----------------------------
-- 5. 模型预测日志表 `ai_predict_log`
-- ----------------------------
DROP TABLE IF EXISTS `ai_predict_log`;
CREATE TABLE `ai_predict_log`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `stock_code` varchar(20) NOT NULL COMMENT '预测股票标的',
  `target_date` date NOT NULL COMMENT '目标预测日期',
  `predict_price` decimal(10, 2) NOT NULL COMMENT '模型预测出的收盘价',
  `actual_price` decimal(10, 2) NULL COMMENT '真实收盘价(未来日终填入以计算模型准确度)',
  `model_version` varchar(30) NULL COMMENT '模型版本标识(如 LSTM_V1)',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '预测生成时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_code_date`(`stock_code`, `target_date`) USING BTREE,
  INDEX `idx_target_date`(`target_date`) USING BTREE
) ENGINE = InnoDB COMMENT = 'LSTM 模型对第二日/后几日价格的预测表';

SET FOREIGN_KEY_CHECKS = 1;