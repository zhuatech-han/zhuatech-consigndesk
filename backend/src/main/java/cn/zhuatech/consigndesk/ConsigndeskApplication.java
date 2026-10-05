// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2
package cn.zhuatech.consigndesk;

import java.time.Clock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * 寄售商品与货主结算应用入口与可测试时钟。 Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech /
 * zhuatech2
 */
@SpringBootApplication(
    excludeName =
        "org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration")
public class ConsigndeskApplication {
  /**
   * 启动寄售商品与货主结算服务。 Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech /
   * zhuatech2
   */
  public static void main(String[] args) {
    SpringApplication.run(ConsigndeskApplication.class, args);
  }

  /**
   * 注入 UTC 时钟，避免业务依赖服务器本地时区。 Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信
   * zhuatech / zhuatech2
   */
  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }
}
