// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2
package cn.zhuatech.consigndesk;

import static org.junit.jupiter.api.Assertions.*;

import java.math.*;
import org.junit.jupiter.api.Test;

/** 分成精度和拒绝静默金额舍入的业务规则测试。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
class ConsignPolicyTest {
  @Test
  void roundingTailAlwaysBalances() {
    var price = new BigDecimal("100.01");
    var owner = ConsignPolicy.ownerShare(price, new BigDecimal("60"));
    assertEquals(new BigDecimal("60.01"), owner);
    assertEquals(new BigDecimal("40.00"), price.subtract(owner));
    assertEquals(price, owner.add(price.subtract(owner)));
  }

  @Test
  void invalidAmountsAndSplitsRejected() {
    for (var s : new String[] {"0", "-1", "0.001", "1000000"})
      assertThrows(Problem.class, () -> ConsignPolicy.money(new BigDecimal(s)));
    for (var s : new String[] {"0", "-1", "100.01", "60.001"})
      assertThrows(
          Problem.class, () -> ConsignPolicy.ownerShare(new BigDecimal("100"), new BigDecimal(s)));
  }

  @Test
  void exactDecimalsAndVersions() {
    assertEquals(new BigDecimal("0.10"), ConsignPolicy.money(new BigDecimal("0.1000")));
    assertThrows(Problem.class, () -> ConsignPolicy.version(2L, 1L));
    assertThrows(Problem.class, () -> ConsignPolicy.version(2L, null));
    ConsignPolicy.version(2L, 2L);
  }
}
