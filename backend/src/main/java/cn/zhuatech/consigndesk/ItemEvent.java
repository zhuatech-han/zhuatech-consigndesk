// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2
package cn.zhuatech.consigndesk;

import jakarta.persistence.*;
import java.time.*;

/** 不可覆盖的商品状态和约定变更记录。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "item_event")
public class ItemEvent {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "item_id", nullable = false)
  public Long itemId;

  @Column(name = "action", nullable = false, length = 60)
  public String action;

  @Column(name = "actor", nullable = false, length = 60)
  public String actor;

  @Column(name = "note", nullable = false, length = 1500)
  public String note;

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;
}
