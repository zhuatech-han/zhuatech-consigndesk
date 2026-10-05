// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2
package cn.zhuatech.consigndesk;

import jakarta.persistence.*;
import java.time.*;

/** 货主档案与部门归属，不保存银行卡或身份证资料。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "consignor")
public class Consignor {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "code", nullable = false, length = 60)
  public String code;

  @Column(name = "name", nullable = false, length = 160)
  public String name;

  @Column(name = "department_id", nullable = false)
  public Long departmentId;

  @Column(name = "contact_note", nullable = false, length = 500)
  public String contactNote;

  @Column(name = "enabled", nullable = false)
  public boolean enabled;

  @Version
  @Column(name = "revision", nullable = false)
  public Long revision;
}
