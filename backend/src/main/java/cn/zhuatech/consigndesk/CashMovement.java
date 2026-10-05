// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2
package cn.zhuatech.consigndesk;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;

/** 线下支付、追回和单次冲正凭据；不执行资金转账。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "cash_movement")
public class CashMovement {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "consignor_id", nullable = false)
  public Long consignorId;

  @Column(name = "department_id", nullable = false)
  public Long departmentId;

  @Column(name = "kind", nullable = false, length = 20)
  public String kind;

  @Column(name = "external_reference", nullable = false, length = 100)
  public String externalReference;

  @Column(name = "amount", nullable = false, precision = 18, scale = 2)
  public BigDecimal amount;

  @Column(name = "reversal_of")
  public Long reversalOf;

  @Column(name = "note", nullable = false, length = 1000)
  public String note;

  @Column(name = "actor", nullable = false, length = 60)
  public String actor;

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;
}
