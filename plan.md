## Plan: 基于SpringBoot的股票智能预测与C端交易系统方案

项目的核心定位已明确为**面向C端的暗黑专业级交易工具**。目前我们优先进行**数据库架构设计**与**后端工程骨架构建**。

**Steps**

**1. 阶段一：数据库架构设计与后端基础架构搭建（当前核心目标）**
*   **后端骨架初始化**：
    *   通过 Spring Initializr 创建 Spring Boot 3 + JDK 17 项目。
    *   引入核心依赖：Spring Web, MySQL Driver, MyBatis-Plus, Redis (Spring Data Redis), JWT 工具包, Knife4j (Swagger增强 API 文档), Lombok。
    *   规划项目标准分层：`controller`, `service`, `mapper`, `entity`, `dto`, `vo`, `config` (统一跨域、MyBatisPlus分页插件配置), `common` (统一返回结果、全局异常处理)。
*   **数据库核心结构脚本编写 (`init.sql`)**：
    *   `sys_user` (用户账户表)：包含用户基本信息、密码(加密存储)与账户初始可用资金(总计模拟100万)。
    *   `stock_info` (股票基础表)：记录全市场股票代码与名称的基础映射。
    *   `trade_order` (交易委托单表)：记录用户的每一次买/卖行为、订单类型(限价/市价)、委托价格、委托数量与订单状态。
    *   `user_position` (用户持仓表)：记录用户的每只股票的整体持股数、可用持股数(T+1规则预留)、平均建仓成本价。
    *   `predict_log` (模型预测日志表)：记录大模型下发的次日及未来多日个股预测价格走势。
*   **基础服务与鉴权搭建**：
    *   基于 `HandlerInterceptor` 或 Spring Security 实现基于 JWT Token 的前端会话拦截与登录态校验。
    *   提供统一的 `Result<T>` 数据返回结构。

**2. 阶段二：C端暗黑风格 Vue3 前端基础设施**
*   初始化 Vue3 + Vite + js 项目，引入 Tailwind CSS，开启并默认激活 `Dark Mode`。
*   引入 Element Plus 组件库，定制全站暗黑主题（类似同花顺、富途牛牛的专业看盘界面）。
*   集成类似于 TradingView/ECharts 的深色图表库预留给 K 线展示。

**3. 阶段三：数据采集服务 & Python (FastAPI + LSTM) 预测系统**
*   SpringBoot 内置定时任务 (`@Scheduled`)，收盘后调用 Python 服务或 AKShare 存储日线数据。
*   开发 Python 预测端点 `/api/v1/predict`，基于开、高、低、收和成交量等指标输出未来的价格预测折线数据。

**4. 阶段四：虚拟撮合交易核心引擎**
*   基于后端 Redis / 数据库锁实现买卖扣除可用资金、冻结持仓的原子化操作。
*   通过定时任务低频拉取实时行情，对处于"待撮合"状态的限价单进行模拟成交。

**5. 阶段五：系统融合与可视化监控**
*   前端利用暗色仪表盘展示用户的当前总资产浮动、昨日盈亏、仓位分布占比。
*   将历史 K 线图与模型预测路线在前端叠加绘制。

**Relevant files**
*   在根目录创建 `backend/` 目录用于存放 SpringBoot 代码，包括 `backend/src/main/resources/sql/init.sql` (待编写的数据库脚本文件)。
*   在根目录创建 `frontend/` 用于初始化前端工作区。

**Verification**
1. 编写完整的 `init.sql` 后，在本地 MySQL (`localhost:3306`) 中创建数据库并成功导入表结构，检查关键表关联外键/索引设计是否合理。
2. 后端服务成功启动并在 `http://localhost:8080/doc.html` 加载 Knife4j 接口文档，无报错。
3. 调通全局异常拦截和一个简单的 `/api/test` 测试接口返回 `{"code": 200, "data": "success"}` 标准格式。

**Decisions**
- 为了契合 C 端交易系统的特点，我们选定专业暗黑主题作为默认且唯一的前端基调，以突出代码和金融工具的极客感。
- 数据库设计考虑 T+1 的A股交易制度，持仓表设计 `quantity` (总持仓) 和 `available_quantity` (可卖出持仓) 进行区分。
- 不引入印花税和交易佣金，订单结算仅按照 价格*数量 计算，简化结算逻辑。