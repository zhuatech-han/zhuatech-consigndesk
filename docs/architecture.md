# ConsignDesk 架构与数据库

知华科技（上海如静知华信息科技有限公司）· https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2。未经书面授权不得商用。

Vue3通过Nginx同源接口访问Java服务，Spring Security保护会话与CSRF，服务层检查主体、权限、部门、状态和版本，JPA写MySQL8.4，Flyway执行`V1__consignment_schema.sql`，Hibernate仅校验结构。后续新增V2，已执行脚本不得修改。

15张应用表：department、access_role、permission、role_permission、nav_menu、account、audit_event、system_setting、dictionary_entry；consignor、consigned_item、item_event、consignment_sale、cash_movement、ledger_entry，另有flyway_schema_history。价钱和有符号台账为decimal(18,2)，比例decimal(5,2)。唯一商品号、货主代码、销售/退款/资金凭据及reversalOf保护重复；外键保留货主、实物、原销售、原资金记录的引用。查询值全部参数绑定。

每个ConsignedItem代表一件实物，状态DRAFT→SUBMITTED→APPROVED→AVAILABLE→SOLD，退款后AVAILABLE；未售取回COLLECTED；收货前取消CANCELLED。退回或撤回回到DRAFT，事件始终追加。收到实物才能在售，批准不代表收货。销售快照留存，退款新增负分成，并不删除销售。

PAYOUT为负台账、RECOVERY为正台账、REVERSAL取原符号相反金额，不能二次冲正。总余额sum(all entries)，已到期余额sum(availableOn<=UTCtoday)，可支付max(0,min(total,matured))，待追回max(0,-total)。使用同一READ_COMMITTED事务锁定基础部门记录，序列化支付、销售、退款、导入及配置检查，避免并发超付；商品和货主额外有JPA版本，拒绝陈旧写入。同步事务内审计，失败连审计回滚。

单实例、单币种、最多10000条同类记录，客户端搜索分页；非SaaS或大规模电商。会话存本实例内存，重启需重新登录；业务、账号、密码和台账存数据库，重启不重置。所有记录日期UTC，业务原文不自动翻译。公司名和咨询品牌分离，不在CSV业务数据中插广告。
