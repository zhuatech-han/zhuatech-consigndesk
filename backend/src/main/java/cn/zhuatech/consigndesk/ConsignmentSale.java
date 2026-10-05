// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2
package cn.zhuatech.consigndesk;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;

/** 外部销售凭据和分成快照，退款后原记录仍保留。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "consignment_sale")
public class ConsignmentSale {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "item_id", nullable = false)
  public Long itemId;

  @Column(name = "consignor_id", nullable = false)
  public Long consignorId;

  @Column(name = "department_id", nullable = false)
  public Long departmentId;

  @Column(name = "external_reference", nullable = false, length = 100)
  public String externalReference;

  @Column(name = "item_name", nullable = false, length = 160)
  public String itemName;

  @Column(name = "price", nullable = false, precision = 18, scale = 2)
  public BigDecimal price;

  @Column(name = "owner_percent", nullable = false, precision = 5, scale = 2)
  public BigDecimal ownerPercent;

  @Column(name = "owner_amount", nullable = false, precision = 18, scale = 2)
  public BigDecimal ownerAmount;

  @Column(name = "store_amount", nullable = false, precision = 18, scale = 2)
  public BigDecimal storeAmount;

  @Column(name = "sold_at", nullable = false)
  public Instant soldAt;

  @Column(name = "available_on", nullable = false)
  public LocalDate availableOn;

  @Column(name = "status", nullable = false, length = 20)
  public String status;

  @Column(name = "refund_reference", length = 100)
  public String refundReference;

  @Column(name = "refund_reason", nullable = false, length = 1000)
  public String refundReason;

  @Column(name = "refunded_at")
  public Instant refundedAt;
}
