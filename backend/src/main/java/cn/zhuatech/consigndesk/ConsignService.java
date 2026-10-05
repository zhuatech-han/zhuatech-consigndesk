// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2
package cn.zhuatech.consigndesk;

import java.math.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 寄售实物、冻结约定、销售退款和货主结算事务；仅记录人工操作，不连接收款系统。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Service
@Transactional
public class ConsignService {
  final Store db;
  final AccessService access;
  final Clock clock;

  public ConsignService(Store db, AccessService access, Clock clock) {
    this.db = db;
    this.access = access;
    this.clock = clock;
  }

  /** 货主档案输入，不接收外部银行账号。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record ConsignorInput(
      String code,
      String name,
      Long departmentId,
      String contactNote,
      Boolean enabled,
      Long revision) {}

  /** 每件实物的价格和寄售边界。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record ItemInput(
      Long consignorId,
      String name,
      String category,
      String conditionNote,
      BigDecimal askingPrice,
      BigDecimal minimumPrice,
      BigDecimal ownerPercent,
      Integer holdDays,
      LocalDate expiresOn,
      Long revision) {}

  /** 状态命令及线下依据，变更日期只在续期命令使用。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record Action(Long revision, String note, String externalReference, LocalDate expiresOn) {}

  /** 销售凭据，不从客户端接收计算出的分成。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record SaleInput(
      String itemReference, Long revision, BigDecimal price, String externalReference) {}

  /** 退款依据，金额只能按原销售全额退款，不重复扣款。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record RefundInput(String externalReference, String note) {}

  /** 已在线下发生的支付/追回/冲正输入；不会进行真实银行操作。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record CashInput(
      Long consignorId,
      String kind,
      BigDecimal amount,
      String externalReference,
      String note,
      Long reversalOf) {}

  /** 有符号应付总余额、到期可支付余额及未到期销售分成。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record Balance(
      Long consignorId,
      String name,
      BigDecimal total,
      BigDecimal payable,
      BigDecimal held,
      BigDecimal recoveryDue) {}

  private LocalDate today() {
    return LocalDate.now(clock);
  }

  private void serial() {
    db.lock(Department.class, 1L);
  }

  private void staff(String permission) {
    access.require(permission);
    if (access.current().consignorId != null || "CONSIGNOR".equals(access.role().scope))
      throw new Problem(403, "FORBIDDEN");
  }

  private Consignor portalOwner() {
    access.require("portal");
    var a = access.current();
    if (a.consignorId == null) throw new Problem(403, "CONSIGNOR_BINDING_REQUIRED");
    var c = db.get(Consignor.class, a.consignorId);
    if (!c.enabled
        || !Objects.equals(c.departmentId, a.departmentId)
        || !"CONSIGNOR".equals(access.role().scope)) throw new Problem(403, "CONSIGNOR_INACTIVE");
    return c;
  }

  private void ownerScope(Long id) {
    var c = db.get(Consignor.class, id);
    if (access.current().consignorId != null) {
      if (!portalOwner().id.equals(c.id)) throw new Problem(403, "OUT_OF_SCOPE");
    } else access.department(c.departmentId);
  }

  private ConsignedItem item(Long id) {
    var v = db.get(ConsignedItem.class, id);
    ownerScope(v.consignorId);
    return v;
  }

  private void readable() {
    if (access.current().consignorId != null) portalOwner();
    else staff("read");
  }

  private String text(String v, int max) {
    return AdminService.text(v, max);
  }

  private String optional(String v, int max) {
    return v == null || v.isBlank() ? "" : text(v, max);
  }

  private void event(ConsignedItem item, String action, String note) {
    var e = new ItemEvent();
    e.itemId = item.id;
    e.action = action;
    e.note = note;
    e.actor = access.current().username;
    e.createdAt = clock.instant();
    db.save(e);
    access.audit(action, item.id, item.departmentId);
  }

  private void state(ConsignedItem i, String... allowed) {
    if (!Set.of(allowed).contains(i.status)) throw new Problem(409, "INVALID_STATE");
  }

  /** 只返回本部门可见货主，货主账号不能读取员工目录。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public List<Consignor> consignors() {
    if (access.current().consignorId != null) return List.of(portalOwner());
    staff("read");
    return db.all(Consignor.class).stream().filter(x -> access.visible(x.departmentId)).toList();
  }

  /** 建立或维护档案，已有实物或账号后不能移动部门，以免突破数据边界。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Consignor saveConsignor(Long id, ConsignorInput v) {
    staff("master");
    serial();
    access.department(v.departmentId);
    db.get(Department.class, v.departmentId);
    var c = id == null ? new Consignor() : db.get(Consignor.class, id);
    if (id != null) {
      access.department(c.departmentId);
      ConsignPolicy.version(c.revision, v.revision);
      if (!Objects.equals(c.departmentId, v.departmentId)
          && (!db.query(ConsignedItem.class, "from ConsignedItem where consignorId=?1", id)
                  .isEmpty()
              || !db.query(Account.class, "from Account where consignorId=?1", id).isEmpty()))
        throw new Problem(409, "DEPARTMENT_LOCKED");
    }
    c.code = text(v.code, 60);
    if (!c.code.matches("[A-Za-z0-9_.-]{1,60}")) throw new Problem(400, "INVALID_CODE");
    c.name = text(v.name, 160);
    c.contactNote = optional(v.contactNote, 500);
    c.departmentId = v.departmentId;
    c.enabled = Boolean.TRUE.equals(v.enabled);
    if (id == null) db.save(c);
    access.audit("CONSIGNOR_SAVE", c.id, c.departmentId);
    return c;
  }

  /** 删除未被商品、账号或台账引用的档案，数据库保留历史引用。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public void deleteConsignor(Long id) {
    staff("master");
    serial();
    var c = db.get(Consignor.class, id);
    access.department(c.departmentId);
    access.audit("CONSIGNOR_DELETE", id, c.departmentId);
    db.delete(c);
  }

  /** 员工或货主读取范围内商品，不提供跨货主记录。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public List<ConsignedItem> items() {
    readable();
    Long owner = access.current().consignorId;
    return db.all(ConsignedItem.class).stream()
        .filter(x -> owner != null ? x.consignorId.equals(owner) : access.visible(x.departmentId))
        .toList();
  }

  /** 明细包含不可覆盖的事件和销售快照。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Map<String, Object> detail(Long id) {
    readable();
    var i = item(id);
    return Map.of(
        "item",
        i,
        "events",
        db.query(ItemEvent.class, "from ItemEvent where itemId=?1 order by id", id),
        "sales",
        db.query(ConsignmentSale.class, "from ConsignmentSale where itemId=?1 order by id", id));
  }

  /** 创建或修改本人草稿；上架后的分成、底价和退货等待期不可改写。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public ConsignedItem saveItem(Long id, ItemInput v) {
    serial();
    var a = access.current();
    var c = a.consignorId != null ? portalOwner() : db.get(Consignor.class, v.consignorId);
    if (a.consignorId == null) {
      staff("process");
      access.department(c.departmentId);
    } else if (v.consignorId != null && !v.consignorId.equals(c.id))
      throw new Problem(403, "OUT_OF_SCOPE");
    if (!c.enabled) throw new Problem(409, "CONSIGNOR_INACTIVE");
    var i = id == null ? new ConsignedItem() : item(id);
    if (id != null) {
      state(i, "DRAFT");
      ConsignPolicy.version(i.revision, v.revision);
      if (!Objects.equals(i.creatorId, a.id) || !i.consignorId.equals(c.id))
        throw new Problem(403, "NOT_CREATOR");
    }
    if (v.expiresOn == null
        || v.expiresOn.isBefore(today())
        || v.expiresOn.isAfter(today().plusDays(730))) throw new Problem(400, "INVALID_DATE");
    if (v.holdDays == null || v.holdDays < 0 || v.holdDays > 90)
      throw new Problem(400, "INVALID_HOLD_DAYS");
    var cats =
        db.query(
            DictionaryEntry.class,
            "from DictionaryEntry where type='category' and code=?1 and enabled=true",
            v.category);
    if (cats.isEmpty()) throw new Problem(400, "INVALID_CATEGORY");
    i.name = text(v.name, 160);
    i.category = v.category;
    i.conditionNote = text(v.conditionNote, 1000);
    i.askingPrice = ConsignPolicy.money(v.askingPrice);
    i.minimumPrice = ConsignPolicy.money(v.minimumPrice);
    if (i.askingPrice.compareTo(i.minimumPrice) < 0) throw new Problem(400, "BELOW_MINIMUM");
    ConsignPolicy.ownerShare(i.askingPrice, v.ownerPercent);
    i.ownerPercent = v.ownerPercent.setScale(2);
    i.holdDays = v.holdDays;
    i.expiresOn = v.expiresOn;
    if (id == null) {
      i.reference = "CI-" + UUID.randomUUID().toString().substring(0, 13).toUpperCase(Locale.ROOT);
      i.consignorId = c.id;
      i.departmentId = c.departmentId;
      i.creatorId = a.id;
      i.status = "DRAFT";
      i.agreementEvidence = "";
      i.createdAt = clock.instant();
      db.save(i);
    }
    event(i, "DRAFT_SAVE", i.name);
    return i;
  }

  /** 删除未提交的本人草稿；已提交过的记录必须保留状态历史。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public void deleteItem(Long id, Long revision) {
    if (access.current().consignorId != null) portalOwner();
    else staff("process");
    serial();
    var i = item(id);
    state(i, "DRAFT");
    ConsignPolicy.version(i.revision, revision);
    if (!i.creatorId.equals(access.current().id)) throw new Problem(403, "NOT_CREATOR");
    var es = db.query(ItemEvent.class, "from ItemEvent where itemId=?1", id);
    if (es.stream().anyMatch(e -> !e.action.equals("DRAFT_SAVE")))
      throw new Problem(409, "HISTORY_RETAINED");
    for (var e : es) db.delete(e);
    access.audit("DRAFT_DELETE", id, i.departmentId);
    db.delete(i);
  }

  /** 校验合法状态、独立审核及实际收货/取回凭据；不把审批等同于实物收到。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public ConsignedItem action(Long id, String action, Action v) {
    serial();
    var i = item(id);
    ConsignPolicy.version(i.revision, v.revision);
    var a = access.current();
    String note = optional(v.note, 1000);
    switch (action) {
      case "submit" -> {
        if (a.consignorId != null) portalOwner();
        else staff("process");
        state(i, "DRAFT");
        if (!i.creatorId.equals(a.id)) throw new Problem(403, "NOT_CREATOR");
        if (i.expiresOn.isBefore(today())) throw new Problem(409, "EXPIRED");
        i.status = "SUBMITTED";
      }
      case "withdraw" -> {
        if (a.consignorId != null) portalOwner();
        else staff("process");
        state(i, "SUBMITTED");
        if (!i.creatorId.equals(a.id)) throw new Problem(403, "NOT_CREATOR");
        i.status = "DRAFT";
      }
      case "approve", "reject" -> {
        staff("process");
        state(i, "SUBMITTED");
        if (i.creatorId.equals(a.id)) throw new Problem(409, "INDEPENDENT_REVIEW_REQUIRED");
        note = text(v.note, 500);
        if (action.equals("approve")) {
          if (i.expiresOn.isBefore(today())) throw new Problem(409, "EXPIRED");
          i.agreementEvidence = note;
          i.status = "APPROVED";
        } else i.status = "DRAFT";
      }
      case "receive" -> {
        staff("process");
        state(i, "APPROVED");
        if (i.expiresOn.isBefore(today())) throw new Problem(409, "EXPIRED");
        i.intakeReference = text(v.externalReference, 100);
        note = text(v.note, 1000);
        i.receivedAt = clock.instant();
        i.status = "AVAILABLE";
        note = i.intakeReference + " | " + note;
      }
      case "renew" -> {
        staff("process");
        state(i, "AVAILABLE", "APPROVED");
        note = text(v.note, 1000);
        if (v.expiresOn == null
            || !v.expiresOn.isAfter(i.expiresOn)
            || v.expiresOn.isBefore(today())
            || v.expiresOn.isAfter(today().plusDays(730))) throw new Problem(400, "INVALID_DATE");
        note = note + " | " + i.expiresOn + " -> " + v.expiresOn;
        i.expiresOn = v.expiresOn;
      }
      case "collect" -> {
        staff("process");
        state(i, "AVAILABLE");
        note = text(v.externalReference, 100) + " | " + text(v.note, 1000);
        i.status = "COLLECTED";
      }
      case "cancel" -> {
        if (a.consignorId != null) portalOwner();
        else staff("process");
        state(i, "DRAFT", "SUBMITTED", "APPROVED");
        if (!i.creatorId.equals(a.id) && a.consignorId != null)
          throw new Problem(403, "NOT_CREATOR");
        note = text(v.note, 1000);
        i.status = "CANCELLED";
      }
      default -> throw new Problem(404, "NOT_FOUND");
    }
    event(i, action.toUpperCase(Locale.ROOT), note);
    return i;
  }

  /** 按当前价格记录一件实物的已发生销售，冻结分成，不允许到期后销售或突破底价。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public ConsignmentSale sell(SaleInput v) {
    staff("process");
    serial();
    var rows =
        db.query(ConsignedItem.class, "from ConsignedItem where reference=?1", v.itemReference);
    if (rows.isEmpty()) throw new Problem(404, "NOT_FOUND");
    var i = item(rows.getFirst().id);
    state(i, "AVAILABLE");
    ConsignPolicy.version(i.revision, v.revision);
    if (i.expiresOn.isBefore(today())) throw new Problem(409, "EXPIRED");
    if (!db.get(Consignor.class, i.consignorId).enabled)
      throw new Problem(409, "CONSIGNOR_INACTIVE");
    var price = ConsignPolicy.money(v.price);
    if (price.compareTo(i.minimumPrice) < 0) throw new Problem(400, "BELOW_MINIMUM");
    var s = new ConsignmentSale();
    s.itemId = i.id;
    s.consignorId = i.consignorId;
    s.departmentId = i.departmentId;
    s.externalReference = text(v.externalReference, 100);
    s.itemName = i.name;
    s.price = price;
    s.ownerPercent = i.ownerPercent;
    s.ownerAmount = ConsignPolicy.ownerShare(price, i.ownerPercent);
    s.storeAmount = price.subtract(s.ownerAmount);
    s.soldAt = clock.instant();
    s.availableOn = today().plusDays(i.holdDays);
    s.status = "SOLD";
    s.refundReason = "";
    db.save(s);
    i.status = "SOLD";
    ledger(i.consignorId, i.departmentId, s.id, null, "SALE", s.ownerAmount, s.availableOn);
    event(i, "SALE", s.externalReference + " | " + s.price + " | owner=" + s.ownerAmount);
    db.flush();
    return s;
  }

  /** 最多300笔导入，任何重复凭据、过期版本、底价或状态错误会回滚全部销售及审计。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public List<ConsignmentSale> importSales(List<SaleInput> rows) {
    staff("process");
    serial();
    if (rows == null || rows.isEmpty() || rows.size() > 300)
      throw new Problem(400, "INVALID_IMPORT");
    var refs = new HashSet<String>();
    var items = new HashSet<String>();
    for (var v : rows) {
      if (v == null
          || !refs.add(text(v.externalReference, 100))
          || !items.add(text(v.itemReference, 60))) throw new Problem(400, "DUPLICATE_IMPORT");
    }
    return rows.stream().map(this::sell).toList();
  }

  /** 原销售全额退款，并归还该实物至待售，原分成冲减；已付金额形成待追回余额。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public ConsignmentSale refund(Long id, RefundInput v) {
    staff("process");
    serial();
    var s = db.get(ConsignmentSale.class, id);
    ownerScope(s.consignorId);
    if (!s.status.equals("SOLD")) throw new Problem(409, "ALREADY_REFUNDED");
    var i = item(s.itemId);
    state(i, "SOLD");
    s.refundReference = text(v.externalReference, 100);
    s.refundReason = text(v.note, 1000);
    s.refundedAt = clock.instant();
    s.status = "REFUNDED";
    i.status = "AVAILABLE";
    ledger(s.consignorId, s.departmentId, s.id, null, "REFUND", s.ownerAmount.negate(), today());
    event(i, "REFUND", s.refundReference + " | " + s.refundReason);
    db.flush();
    return s;
  }

  /** 员工或货主查看范围内销售快照。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public List<ConsignmentSale> sales() {
    readable();
    var owner = access.current().consignorId;
    return db.all(ConsignmentSale.class).stream()
        .filter(x -> owner != null ? x.consignorId.equals(owner) : access.visible(x.departmentId))
        .toList();
  }

  private void ledger(
      Long owner, Long dept, Long sale, Long cash, String kind, BigDecimal amount, LocalDate day) {
    var e = new LedgerEntry();
    e.consignorId = owner;
    e.departmentId = dept;
    e.saleId = sale;
    e.cashId = cash;
    e.kind = kind;
    e.amount = amount;
    e.availableOn = day;
    e.createdAt = clock.instant();
    db.save(e);
  }

  private Balance calculate(Consignor c) {
    var rows = db.query(LedgerEntry.class, "from LedgerEntry where consignorId=?1", c.id);
    var total = BigDecimal.ZERO.setScale(2);
    var matured = total;
    for (var e : rows) {
      total = total.add(e.amount);
      if (!e.availableOn.isAfter(today())) matured = matured.add(e.amount);
    }
    var payable = total.min(matured).max(BigDecimal.ZERO);
    var held = total.subtract(matured).max(BigDecimal.ZERO);
    return new Balance(c.id, c.name, total, payable, held, total.negate().max(BigDecimal.ZERO));
  }

  /** 按不可改写的台账计算余额，未到期分成不允许提前支付，负余额明确显示待追回。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Map<String, Object> account(Long id) {
    if (access.current().consignorId != null) portalOwner();
    else staff("finance");
    ownerScope(id);
    var c = db.get(Consignor.class, id);
    return Map.of(
        "consignor",
        c,
        "balance",
        calculate(c),
        "ledger",
        db.query(LedgerEntry.class, "from LedgerEntry where consignorId=?1 order by id", id),
        "movements",
        db.query(CashMovement.class, "from CashMovement where consignorId=?1 order by id", id));
  }

  /** 汇总本人或部门的应付、冻结和待追回金额。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public List<Balance> balances() {
    if (access.current().consignorId != null) return List.of(calculate(portalOwner()));
    staff("finance");
    return db.all(Consignor.class).stream()
        .filter(c -> access.visible(c.departmentId))
        .map(this::calculate)
        .toList();
  }

  /**
   * 原子登记线下凭据，防止重复付款、超余额付款和追回超量；冲正追加相反金额且只能一次。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。
   */
  public CashMovement cash(CashInput v) {
    staff("finance");
    serial();
    ownerScope(v.consignorId);
    var c = db.get(Consignor.class, v.consignorId);
    var b = calculate(c);
    var m = new CashMovement();
    m.consignorId = c.id;
    m.departmentId = c.departmentId;
    m.kind = text(v.kind, 20);
    m.externalReference = text(v.externalReference, 100);
    m.note = text(v.note, 1000);
    m.actor = access.current().username;
    m.createdAt = clock.instant();
    BigDecimal signed;
    switch (m.kind) {
      case "PAYOUT" -> {
        if (v.reversalOf != null) throw new Problem(400, "INVALID_INPUT");
        m.amount = ConsignPolicy.money(v.amount);
        if (m.amount.compareTo(b.payable) > 0) throw new Problem(409, "INSUFFICIENT_PAYABLE");
        signed = m.amount.negate();
      }
      case "RECOVERY" -> {
        if (v.reversalOf != null) throw new Problem(400, "INVALID_INPUT");
        m.amount = ConsignPolicy.money(v.amount);
        if (m.amount.compareTo(b.recoveryDue) > 0) throw new Problem(409, "EXCESS_RECOVERY");
        signed = m.amount;
      }
      case "REVERSAL" -> {
        if (v.reversalOf == null) throw new Problem(400, "INVALID_INPUT");
        var old = db.get(CashMovement.class, v.reversalOf);
        if (!old.consignorId.equals(c.id) || old.kind.equals("REVERSAL"))
          throw new Problem(400, "INVALID_REVERSAL");
        if (!db.query(CashMovement.class, "from CashMovement where reversalOf=?1", old.id)
            .isEmpty()) throw new Problem(409, "ALREADY_REVERSED");
        m.reversalOf = old.id;
        m.amount = old.amount;
        if (v.amount != null && v.amount.compareTo(m.amount) != 0)
          throw new Problem(400, "INVALID_MONEY");
        signed = old.kind.equals("PAYOUT") ? m.amount : m.amount.negate();
      }
      default -> throw new Problem(400, "INVALID_CASH_KIND");
    }
    db.save(m);
    ledger(c.id, c.departmentId, null, m.id, m.kind, signed, today());
    access.audit("CASH_" + m.kind, m.id, c.departmentId);
    db.flush();
    return m;
  }

  /** 配置目录供表单使用；普通员工只见本部门，不泄露账号及角色目录。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Map<String, Object> meta() {
    access.current();
    var owner = access.current().consignorId;
    if (owner != null) portalOwner();
    else staff("read");
    return Map.of(
        "categories",
        db.query(
            DictionaryEntry.class, "from DictionaryEntry where type='category' and enabled=true"),
        "departments",
        owner != null
            ? List.of()
            : db.all(Department.class).stream().filter(d -> access.visible(d.id)).toList(),
        "settings",
        db.all(SystemSetting.class));
  }

  /** 真实商品状态、销售净额和门店分成，不把付款登记当新增销售。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Map<String, Object> dashboard() {
    staff("dashboard");
    var is = items();
    var ss = sales();
    var counts = new LinkedHashMap<String, Long>();
    for (var status :
        List.of("DRAFT", "SUBMITTED", "APPROVED", "AVAILABLE", "SOLD", "COLLECTED", "CANCELLED"))
      counts.put(status, is.stream().filter(i -> i.status.equals(status)).count());
    var active = ss.stream().filter(s -> s.status.equals("SOLD")).toList();
    return Map.of(
        "counts",
        counts,
        "expired",
        is.stream()
            .filter(i -> i.status.equals("AVAILABLE") && i.expiresOn.isBefore(today()))
            .count(),
        "netSales",
        active.stream().map(s -> s.price).reduce(BigDecimal.ZERO, BigDecimal::add),
        "storeShare",
        active.stream().map(s -> s.storeAmount).reduce(BigDecimal.ZERO, BigDecimal::add),
        "ownerShare",
        active.stream().map(s -> s.ownerAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
  }

  /** 仅导出本范围业务 CSV，对文本公式前缀中和，不添加推广信息。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public String export(String type) {
    staff("report");
    var out = new StringBuilder("\uFEFF");
    if (type.equals("items")) {
      out.append(
          "reference,consignorId,name,status,askingPrice,minimumPrice,ownerPercent,holdDays,expiresOn\r\n");
      for (var i : items())
        row(
            out,
            i.reference,
            i.consignorId,
            i.name,
            i.status,
            i.askingPrice,
            i.minimumPrice,
            i.ownerPercent,
            i.holdDays,
            i.expiresOn);
    } else if (type.equals("sales")) {
      out.append(
          "reference,itemId,consignorId,itemName,status,price,ownerAmount,storeAmount,soldAt,availableOn\r\n");
      for (var s : sales())
        row(
            out,
            s.externalReference,
            s.itemId,
            s.consignorId,
            s.itemName,
            s.status,
            s.price,
            s.ownerAmount,
            s.storeAmount,
            s.soldAt,
            s.availableOn);
    } else if (type.equals("ledger")) {
      staff("finance");
      out.append("id,consignorId,kind,amount,availableOn,saleId,cashId,createdAt\r\n");
      for (var e : db.all(LedgerEntry.class))
        if (access.visible(e.departmentId))
          row(
              out,
              e.id,
              e.consignorId,
              e.kind,
              e.amount,
              e.availableOn,
              e.saleId,
              e.cashId,
              e.createdAt);
    } else throw new Problem(404, "NOT_FOUND");
    return out.toString();
  }

  private void row(StringBuilder out, Object... cells) {
    for (int n = 0; n < cells.length; n++) {
      if (n > 0) out.append(',');
      var s = cells[n] == null ? "" : cells[n].toString();
      if (cells[n] instanceof String
          && !s.stripLeading().isEmpty()
          && "=+-@".indexOf(s.stripLeading().charAt(0)) >= 0) s = "'" + s;
      out.append('"').append(s.replace("\"", "\"\"")).append('"');
    }
    out.append("\r\n");
  }
}
