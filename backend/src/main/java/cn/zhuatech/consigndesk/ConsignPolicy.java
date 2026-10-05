// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2
package cn.zhuatech.consigndesk;

import java.math.*;

/** 精确分成、金额边界和版本校验。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
public final class ConsignPolicy {
  /** 不静默舍入输入金额，禁止零金额及超界。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static BigDecimal money(BigDecimal v) {
    if (v == null
        || v.signum() <= 0
        || v.compareTo(new BigDecimal("999999.99")) > 0
        || v.stripTrailingZeros().scale() > 2) throw new Problem(400, "INVALID_MONEY");
    return v.setScale(2);
  }

  /** 冻结实际销售价分成，尾差归门店，两部分始终合计销售价。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static BigDecimal ownerShare(BigDecimal price, BigDecimal percent) {
    money(price);
    if (percent == null
        || percent.signum() <= 0
        || percent.compareTo(new BigDecimal("100")) > 0
        || percent.stripTrailingZeros().scale() > 2) throw new Problem(400, "INVALID_PERCENT");
    return price.multiply(percent).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
  }

  /** 过期页面拒绝覆盖已被其他人修改的记录。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static void version(Long actual, Long input) {
    if (input == null || !input.equals(actual)) throw new Problem(409, "VERSION_CONFLICT");
  }
}
