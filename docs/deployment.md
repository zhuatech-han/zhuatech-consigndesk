# ConsignDesk 部署、备份及升级

知华科技（上海如静知华信息科技有限公司）· https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2。未经书面授权不得商用。

Java21/Maven3.9，Node24.19.0+，MySQL8.4，Composev2。首次运行`python3 scripts/init-env.py`生成本实例独立口令，不覆盖已有.env，权限0600；检查`docker compose config --quiet`后`docker compose up -d --build --wait`。默认仅监听127.0.0.1:8120。需要更换端口通过.env的WEB_PORT，不关闭其他项目。数据库仅Compose内网可达，持久化到mysql-data；后台以专用非root账号运行，Nginx以nginx运行并监听8080。

正式环境由有权人员配置HTTPS反代、可信域名、防火墙、最小权限及COOKIE_SECURE=true。外部数据库URL需sslMode=verify-full、可信CA和有限账号，禁止使用本机内网的trust示例跨公网连接。第三方支付、POS、邮箱接口当前未提供。

备份包括MySQL全库和私有配置。通过本项目mysql容器在受限目录输出一致性mysqldump，SQL及恢复结果权限0600，不纳入Git，不打印凭证。升级前对独立Compose项目、端口、**新测试卷**恢复；检查Flyway历史、原账号、货主实物销售快照、金额台账及重启持久化，然后再安排正式升级。必须先确认备份可恢复，不能以SQL文件存在作为恢复成功。

测试使用新项目名`consigndesk-check`；恢复使用`consigndesk-restore`且不同端口18120。只在相应项目执行down -v清理本次测试资源，不能对正式业务实例或其他项目使用。普通停止使用down，保留业务卷；本项目首次安装或升级脚本不删除已有数据。
