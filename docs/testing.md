# ConsignDesk 验收方法

知华科技（上海如静知华信息科技有限公司）· https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2。未经书面授权不得商用。

后端`mvn -B -f backend/pom.xml spotless:check test package`，前端`npm ci --no-audit --no-fund`、`npm run format:check`、`npm run lint`、`npm test`、`npm run build`。Dockerfile执行全量测试，不跳过。HTTP/JPA覆盖空库迁移、完整寄售销售、等待期、已付款后退款与追回、分成尾差、重复凭据、冲正一次、整批回滚、绑定/权限/状态/版本和账号保护。H2不替代MySQL。

前端回归包括真实App.vue脚本的SSR组件状态检查：会话失效清空表单、写入失败保留输入、写入成功但刷新失败明确已保存、范围改变清除旧数据、写入结果不明禁止重复确认。请求测试覆盖正文超时、失效会话、权限拒绝、CSV下载和写入不自动重发；CSV及资金、草稿历史测试覆盖相应边界。组件状态检查不等于浏览器点击或视觉验收。

`TEST_URL=http://127.0.0.1:8120 python3 scripts/smoke-test.py`只允许全新可丢弃测试库。测试创建独立TEST货主、账号、实物、销售及资金凭据，验证MySQL外键/事务、并发付款、部门和本人隔离、状态、导出公式中和、迁移和重启后保留。私有口令只保存ignored output供页面验收，不进入Git，不在报告显示。

真实页面需要验证：货主/员工/财务登录；实物草稿、提交、独立审核、收货、销售、退款；款项登记、余额和冲正；账号角色分类参数；CSV预览、报表下载；分页搜索筛选和英语；390×844手机菜单、表格及表单；失败后保留输入。截图逐张打开检查，不得有Apple密码建议、浏览器浮标或长图拼接缝，不能使用修图或效果图代替当前页面。

执行 `python3 scripts/persistence-check.py --capture` 保存源测试实例响应；恢复后用对应 `TEST_URL` 执行同脚本（不加 `--capture`）核对原账号、商品状态、销售快照和台账。仅供本项目独立TEST实例，读取ignored output中的测试口令，不适用于正式业务资料。

备份恢复须将完整测试库导出至权限0600的私有SQL，在另一个Compose项目/端口/全新卷导入，核对原账号、Flyway、业务和台账，重启再核对；只清理本次测试资源。发布前`python3 scripts/release-check.py`扫描品牌、原二维码摘要、README实际图片、许可及已知秘密格式，执行`git diff --check`并审阅完整差异。页面、截图、恢复或发布检查未完成时不能写全部验收通过。
