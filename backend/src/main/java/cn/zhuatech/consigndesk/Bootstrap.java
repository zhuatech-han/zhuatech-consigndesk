// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.consigndesk;

import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 空库初始化员工及货主权限，货主、商品、销售和结算由实际操作建立。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Component
public class Bootstrap implements ApplicationRunner {
  final Store db;
  final BCryptPasswordEncoder encoder;

  @Value("${consigndesk.admin-username}")
  String username;

  @Value("${consigndesk.admin-password}")
  String password;

  public Bootstrap(Store db, BCryptPasswordEncoder encoder) {
    this.db = db;
    this.encoder = encoder;
  }

  /** 仅第一次建立管理员，不在重启时重设密码。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (!db.all(Account.class).isEmpty()) return;
    AdminService.validatePassword(password);
    if (!username.matches("[a-zA-Z0-9_.-]{3,60}"))
      throw new IllegalArgumentException("INVALID_ADMIN_USERNAME");
    var d = new Department();
    d.name = "寄售运营 / Consignment operations";
    db.save(d);
    var all = new HashSet<String>();
    for (var v :
        new String[][] {
          {"dashboard", "工作台 / Workspace"},
          {"read", "商品与销售 / Items and sales"},
          {"master", "货主档案 / Consignors"},
          {"process", "收货与寄售 / Intake and consignment"},
          {"finance", "结算记录 / Settlements"},
          {"report", "报表导出 / Reports"},
          {"admin", "账号与设置 / Administration"},
          {"audit", "操作记录 / Audit"},
          {"portal", "本人寄售 / My consignment"}
        }) {
      var p = new Permission();
      p.code = v[0];
      p.name = v[1];
      db.save(p);
      if (!v[0].equals("portal")) all.add(v[0]);
    }
    var admin = role("管理员 / Administrator", "ALL", all);
    role(
        "门店运营 / Store operations",
        "DEPARTMENT",
        Set.of("dashboard", "read", "master", "process", "report"));
    role("财务 / Finance", "DEPARTMENT", Set.of("dashboard", "read", "finance", "report"));
    role("货主 / Consignor", "CONSIGNOR", Set.of("portal"));
    var a = new Account();
    a.username = username;
    a.displayName = "管理员 / Administrator";
    a.passwordHash = encoder.encode(password);
    a.roleId = admin.id;
    a.departmentId = d.id;
    a.enabled = true;
    db.save(a);
    int pos = 0;
    for (var v :
        new String[][] {
          {"dashboard", "工作台", "Workspace", "dashboard"},
          {"consignors", "货主", "Consignors", "master"},
          {"items", "寄售商品", "Consigned items", "read"},
          {"sales", "销售与退款", "Sales & returns", "read"},
          {"settlements", "货主结算", "Settlements", "finance"},
          {"portal", "我的寄售", "My consignment", "portal"},
          {"reports", "统计与导出", "Reports", "report"},
          {"admin", "账号与设置", "Administration", "admin"},
          {"audit", "操作记录", "Audit", "audit"}
        }) {
      var m = new NavMenu();
      m.code = v[0];
      m.name = v[1];
      m.nameEn = v[2];
      m.permissionCode = v[3];
      m.position = pos++;
      m.enabled = true;
      db.save(m);
    }
    for (var v :
        new String[][] {
          {"companyName", "寄售运营 / Consignment operations"},
          {"currency", "CNY"},
          {
            "consignmentNotice",
            "每个商品编号对应一件实物，约定须线下核实。 / Each item represents one physical piece; verify the agreement externally."
          }
        }) {
      var s = new SystemSetting();
      s.code = v[0];
      s.value = v[1];
      db.save(s);
    }
    for (var v :
        new String[][] {
          {"CLOTHING", "服饰", "Clothing"},
          {"COLLECTIBLE", "收藏品", "Collectibles"},
          {"HOME", "家居用品", "Homeware"}
        }) {
      var e = new DictionaryEntry();
      e.type = "category";
      e.code = v[0];
      e.name = v[1];
      e.nameEn = v[2];
      e.enabled = true;
      db.save(e);
    }
  }

  private AccessRole role(String name, String scope, Set<String> permissions) {
    var r = new AccessRole();
    r.name = name;
    r.scope = scope;
    r.permissions = new HashSet<>(permissions);
    return db.save(r);
  }
}
