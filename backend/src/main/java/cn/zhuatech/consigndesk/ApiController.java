// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2
package cn.zhuatech.consigndesk;

import java.util.*;
import org.springframework.http.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/** 真实寄售及管理接口，所有业务写入经服务层范围及状态校验。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@RestController
@RequestMapping("/api")
public class ApiController {
  final ConsignService business;
  final AdminService admin;
  final AccessService access;
  final Store db;

  public ApiController(
      ConsignService business, AdminService admin, AccessService access, Store db) {
    this.business = business;
    this.admin = admin;
    this.access = access;
    this.db = db;
  }

  /** 配置目录。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/meta")
  public Object meta() {
    return business.meta();
  }

  /** 工作台统计。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/dashboard")
  public Object dashboard() {
    return business.dashboard();
  }

  /** 范围内货主目录。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/consignors")
  public Object consignors() {
    return business.consignors();
  }

  /** 建立货主。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/consignors")
  public Object consignorCreate(@RequestBody ConsignService.ConsignorInput v) {
    return business.saveConsignor(null, v);
  }

  /** 编辑货主档案。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PutMapping("/consignors/{id}")
  public Object consignorEdit(@PathVariable Long id, @RequestBody ConsignService.ConsignorInput v) {
    return business.saveConsignor(id, v);
  }

  /** 删除未引用货主。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @DeleteMapping("/consignors/{id}")
  public Object consignorDelete(@PathVariable Long id) {
    business.deleteConsignor(id);
    return Map.of("ok", true);
  }

  /** 商品列表。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/items")
  public Object items() {
    return business.items();
  }

  /** 状态和销售历史。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/items/{id}")
  public Object detail(@PathVariable Long id) {
    return business.detail(id);
  }

  /** 新建实物草稿。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/items")
  public Object itemCreate(@RequestBody ConsignService.ItemInput v) {
    return business.saveItem(null, v);
  }

  /** 编辑本人草稿。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PutMapping("/items/{id}")
  public Object itemEdit(@PathVariable Long id, @RequestBody ConsignService.ItemInput v) {
    return business.saveItem(id, v);
  }

  /** 删除未提交草稿。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @DeleteMapping("/items/{id}")
  public Object itemDelete(@PathVariable Long id, @RequestParam Long revision) {
    business.deleteItem(id, revision);
    return Map.of("ok", true);
  }

  /** 受控状态命令。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/items/{id}/{action}")
  public Object action(
      @PathVariable Long id, @PathVariable String action, @RequestBody ConsignService.Action v) {
    return business.action(id, action, v);
  }

  /** 销售列表。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/sales")
  public Object sales() {
    return business.sales();
  }

  /** 登记外部销售凭据。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/sales")
  public Object sale(@RequestBody ConsignService.SaleInput v) {
    return business.sell(v);
  }

  /** 原子批量销售导入。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/sales/import")
  public Object importSales(@RequestBody List<ConsignService.SaleInput> v) {
    return business.importSales(v);
  }

  /** 已发生的原销售全额退款。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/sales/{id}/refund")
  public Object refund(@PathVariable Long id, @RequestBody ConsignService.RefundInput v) {
    return business.refund(id, v);
  }

  /** 到期、冻结和负余额汇总。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/balances")
  public Object balances() {
    return business.balances();
  }

  /** 货主应付台账和凭据。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/accounts/{id}")
  public Object account(@PathVariable Long id) {
    return business.account(id);
  }

  /** 人工支付、追回、冲正记录，不触发真实资金操作。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/cash")
  public Object cash(@RequestBody ConsignService.CashInput v) {
    return business.cash(v);
  }

  /** 范围过滤且防 CSV 公式的导出。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/export/{type}")
  public ResponseEntity<String> export(@PathVariable String type) {
    return ResponseEntity.ok()
        .header(
            HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=consignment-" + type + ".csv")
        .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
        .body(business.export(type));
  }

  /** 账号及基础管理目录。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/admin/{type}")
  public Object adminList(@PathVariable String type) {
    return admin.list(type);
  }

  /** 管理资源新建，账号绑定和最后管理员保护。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/admin/{type}")
  public Object adminCreate(@PathVariable String type, @RequestBody AdminService.Input v) {
    return admin.save(type, null, v);
  }

  /** 管理资源更新，内建业务键不可替换。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PutMapping("/admin/{type}/{id}")
  public Object adminEdit(
      @PathVariable String type, @PathVariable Long id, @RequestBody AdminService.Input v) {
    return admin.save(type, id, v);
  }

  /** 管理资源引用保护删除。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @DeleteMapping("/admin/{type}/{id}")
  public Object adminDelete(@PathVariable String type, @PathVariable Long id) {
    admin.delete(type, id);
    return Map.of("ok", true);
  }

  /** 部门范围内操作审计，货主无权读取员工记录。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/audit")
  @Transactional(readOnly = true)
  public Object audit() {
    access.require("audit");
    if (access.current().consignorId != null) throw new Problem(403, "FORBIDDEN");
    return db.all(AuditEvent.class).stream().filter(x -> access.visible(x.departmentId)).toList();
  }
}
