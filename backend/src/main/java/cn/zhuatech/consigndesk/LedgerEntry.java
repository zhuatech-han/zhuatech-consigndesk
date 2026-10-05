// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2
package cn.zhuatech.consigndesk;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;

/** 货主分成、退款及付款的不可修改复式来源台账，金额正负表示应付变化。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "ledger_entry")
public class LedgerEntry {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "consignor_id", nullable = false)
  public Long consignorId;

  @Column(name = "department_id", nullable = false)
  public Long departmentId;

  @Column(name = "sale_id")
  public Long saleId;

  @Column(name = "cash_id")
  public Long cashId;

  @Column(name = "kind", nullable = false, length = 20)
  public String kind;

  @Column(name = "amount", nullable = false, precision = 18, scale = 2)
  public BigDecimal amount;

  @Column(name = "available_on", nullable = false)
  public LocalDate availableOn;

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;
}
