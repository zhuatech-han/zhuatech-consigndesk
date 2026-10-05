// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.consigndesk;

import java.time.*;
import java.util.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 管理账号、角色、货主绑定、权限目录、菜单、部门、字典和参数，保护最后的管理员。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Service
@Transactional
public class AdminService {
  final Store db;
  final AccessService access;
  final BCryptPasswordEncoder encoder;

  public AdminService(Store db, AccessService access, BCryptPasswordEncoder encoder) {
    this.db = db;
    this.access = access;
    this.encoder = encoder;
  }

  /** 有限管理输入，不接收密码哈希或任意实体类型。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record Input(
      String name,
      String nameEn,
      String code,
      String username,
      String displayName,
      String password,
      Long roleId,
      Long departmentId,
      Long consignorId,
      Boolean enabled,
      String scope,
      Set<String> permissions,
      String permissionCode,
      Integer position,
      String type,
      String value) {}

  /** 返回管理目录；Account 的散列字段被忽略。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public List<?> list(String type) {
    access.require("admin");
    if (!"ALL".equals(access.role().scope)) throw new Problem(403, "OUT_OF_SCOPE");
    return db.all(type(type));
  }

  /** 新建或更新经过校验的管理资源。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object save(String type, Long id, Input v) {
    access.require("admin");
    if (!"ALL".equals(access.role().scope)) throw new Problem(403, "OUT_OF_SCOPE");
    db.lock(Department.class, 1L);
    // Capture the authorized actor before an account edit invalidates their current session.
    access.audit("ADMIN_UPDATE_" + type, id == null ? "NEW" : id, access.current().departmentId);
    Object result;
    switch (type) {
      case "users" -> {
        var a = id == null ? new Account() : db.get(Account.class, id);
        protectLink(a, v);
        a.username = text(v.username, 60);
        if (!a.username.matches("[a-zA-Z0-9_.-]{3,60}")) throw new Problem(400, "INVALID_USERNAME");
        a.displayName = text(v.displayName, 120);
        a.roleId = db.get(AccessRole.class, v.roleId).id;
        a.departmentId = db.get(Department.class, v.departmentId).id;
        a.consignorId = v.consignorId == null ? null : db.get(Consignor.class, v.consignorId).id;
        a.enabled = Boolean.TRUE.equals(v.enabled);
        if (id == null || v.password != null && !v.password.isBlank()) {
          validatePassword(v.password);
          a.passwordHash = encoder.encode(v.password);
        }
        result = id == null ? db.save(a) : a;
      }
      case "roles" -> {
        var r = id == null ? new AccessRole() : db.get(AccessRole.class, id);
        r.name = text(v.name, 120);
        if (v.scope == null || !Set.of("ALL", "DEPARTMENT", "CONSIGNOR").contains(v.scope))
          throw new Problem(400, "INVALID_SCOPE");
        r.scope = v.scope;
        var known = new HashSet<>(db.all(Permission.class).stream().map(x -> x.code).toList());
        if (v.permissions == null || !known.containsAll(v.permissions))
          throw new Problem(400, "INVALID_PERMISSION");
        if (v.permissions.contains("admin") && !v.scope.equals("ALL"))
          throw new Problem(400, "INVALID_SCOPE");
        r.permissions = new HashSet<>(v.permissions);
        result = id == null ? db.save(r) : r;
      }
      case "permissions" -> {
        if (id == null) throw new Problem(400, "REGISTERED_PERMISSIONS_ONLY");
        var r = db.get(Permission.class, id);
        r.name = text(v.name, 120);
        result = r;
      }
      case "menus" -> {
        if (id == null) throw new Problem(400, "REGISTERED_MENUS_ONLY");
        var r = db.get(NavMenu.class, id);
        r.name = text(v.name, 120);
        r.nameEn = text(v.nameEn, 120);
        if (db.query(Permission.class, "from Permission where code=?1", v.permissionCode).isEmpty())
          throw new Problem(400, "INVALID_PERMISSION");
        r.permissionCode = v.permissionCode;
        r.position = v.position == null ? 0 : v.position;
        r.enabled = Boolean.TRUE.equals(v.enabled);
        result = r;
      }
      case "departments" -> {
        var r = id == null ? new Department() : db.get(Department.class, id);
        r.name = text(v.name, 120);
        result = id == null ? db.save(r) : r;
      }
      case "dictionaries" -> {
        var r = id == null ? new DictionaryEntry() : db.get(DictionaryEntry.class, id);
        if (id != null && (!Objects.equals(r.type, v.type) || !Objects.equals(r.code, v.code)))
          throw new Problem(409, "DICTIONARY_KEY_IMMUTABLE");
        r.type = text(v.type, 60);
        r.code = text(v.code, 60);
        r.name = text(v.name, 120);
        r.nameEn = text(v.nameEn, 120);
        r.enabled = Boolean.TRUE.equals(v.enabled);
        result = id == null ? db.save(r) : r;
      }
      case "settings" -> {
        if (id == null) throw new Problem(400, "REGISTERED_SETTINGS_ONLY");
        var r = db.get(SystemSetting.class, id);
        var value = text(v.value, 6000);
        switch (r.code) {
          case "currency" -> {
            if ((!db.all(ConsignedItem.class).isEmpty()) && !value.equals(r.value))
              throw new Problem(409, "CURRENCY_LOCKED");
            if (Currency.getInstance(value).getDefaultFractionDigits() != 2)
              throw new Problem(400, "INVALID_CURRENCY");
          }
          case "companyName" -> {
            if (value.length() > 160) throw new Problem(400, "INVALID_INPUT");
          }
          case "consignmentNotice" -> {
            if (value.length() > 1000) throw new Problem(400, "INVALID_INPUT");
          }
          default -> throw new Problem(400, "INVALID_SETTING");
        }
        r.value = value;
        result = r;
      }
      default -> throw new Problem(404, "NOT_FOUND");
    }
    protectAdmin();
    for (var a : db.all(Account.class)) validateLinkedRole(a);
    return result;
  }

  /** 外键保护引用；不允许删除系统内建权限、菜单、参数和基础部门。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public void delete(String type, Long id) {
    access.require("admin");
    if (!"ALL".equals(access.role().scope)) throw new Problem(403, "OUT_OF_SCOPE");
    db.lock(Department.class, 1L);
    if (Set.of("permissions", "menus", "settings").contains(type)
        || type.equals("departments") && id == 1L) throw new Problem(409, "BUILTIN_RESOURCE");
    access.audit("ADMIN_DELETE_" + type, id, access.current().departmentId);
    db.delete(db.get(type(type), id));
    protectAdmin();
  }

  /** 验证管理员初始化及修改密码，不允许弱默认密码。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static void validatePassword(String value) {
    if (value == null
        || value.length() < 12
        || value.length() > 72
        || value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72
        || !value.matches(".*[a-z].*")
        || !value.matches(".*[A-Z].*")
        || !value.matches(".*[0-9].*")) throw new Problem(400, "WEAK_PASSWORD");
  }

  /** 检查必填文本长度。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static String text(String value, int max) {
    if (value == null || value.isBlank() || value.length() > max)
      throw new Problem(400, "INVALID_INPUT");
    return value.trim();
  }

  /** 防止货主绑定被编辑为员工权限，或货主角色取得员工数据范围。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public void validateLinkedRole(Account a) {
    var r = db.get(AccessRole.class, a.roleId);
    if (a.consignorId != null) {
      var c = db.get(Consignor.class, a.consignorId);
      if (!r.scope.equals("CONSIGNOR")
          || !r.permissions.equals(Set.of("portal"))
          || !Objects.equals(c.departmentId, a.departmentId))
        throw new Problem(409, "CONSIGNOR_ROLE_REQUIRED");
    } else if (r.scope.equals("CONSIGNOR") || r.permissions.contains("portal"))
      throw new Problem(409, "CONSIGNOR_BINDING_REQUIRED");
  }

  private void protectLink(Account a, Input v) {
    if (a.id != null && !Objects.equals(a.consignorId, v.consignorId))
      throw new Problem(409, "CONSIGNOR_BINDING_IMMUTABLE");
  }

  private Class<?> type(String type) {
    return switch (type) {
      case "users" -> Account.class;
      case "roles" -> AccessRole.class;
      case "permissions" -> Permission.class;
      case "menus" -> NavMenu.class;
      case "departments" -> Department.class;
      case "dictionaries" -> DictionaryEntry.class;
      case "settings" -> SystemSetting.class;
      default -> throw new Problem(404, "NOT_FOUND");
    };
  }

  private void protectAdmin() {
    boolean present =
        db.all(Account.class).stream()
            .anyMatch(
                a ->
                    a.enabled
                        && db.get(AccessRole.class, a.roleId).permissions.contains("admin")
                        && db.get(AccessRole.class, a.roleId).scope.equals("ALL"));
    if (!present) throw new Problem(409, "LAST_ADMIN");
  }
}
