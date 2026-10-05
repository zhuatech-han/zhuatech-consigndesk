// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2
package cn.zhuatech.consigndesk;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;

/** 一件寄售实物的冻结约定、物理状态及版本。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "consigned_item")
public class ConsignedItem {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "reference", nullable = false, length = 60)
  public String reference;

  @Column(name = "consignor_id", nullable = false)
  public Long consignorId;

  @Column(name = "department_id", nullable = false)
  public Long departmentId;

  @Column(name = "name", nullable = false, length = 160)
  public String name;

  @Column(name = "category", nullable = false, length = 60)
  public String category;

  @Column(name = "condition_note", nullable = false, length = 1000)
  public String conditionNote;

  @Column(name = "asking_price", nullable = false, precision = 18, scale = 2)
  public BigDecimal askingPrice;

  @Column(name = "minimum_price", nullable = false, precision = 18, scale = 2)
  public BigDecimal minimumPrice;

  @Column(name = "owner_percent", nullable = false, precision = 5, scale = 2)
  public BigDecimal ownerPercent;

  @Column(name = "hold_days", nullable = false)
  public Integer holdDays;

  @Column(name = "expires_on", nullable = false)
  public LocalDate expiresOn;

  @Column(name = "status", nullable = false, length = 20)
  public String status;

  @Column(name = "creator_id", nullable = false)
  public Long creatorId;

  @Column(name = "agreement_evidence", nullable = false, length = 500)
  public String agreementEvidence;

  @Column(name = "intake_reference", length = 100)
  public String intakeReference;

  @Column(name = "received_at")
  public Instant receivedAt;

  @Version
  @Column(name = "revision", nullable = false)
  public Long revision;

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;
}
