// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2
package cn.zhuatech.consigndesk;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import java.math.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.*;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 实际HTTP、权限、迁移、分成、退款、支付及事务回滚验收。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(ConsignIntegrationTest.TimeConfig.class)
class ConsignIntegrationTest {
  static final String PASSWORD = "Aa9" + UUID.randomUUID();

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry r) {
    r.add(
        "spring.datasource.url",
        () -> "jdbc:h2:mem:consign;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
    r.add("spring.datasource.username", () -> "sa");
    r.add("spring.datasource.password", () -> "");
    r.add("consigndesk.admin-password", () -> PASSWORD);
  }

  /** 控制等待期与到期的真实业务时钟，避免依赖机器时间。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  static class MutableClock extends Clock {
    Instant at = Instant.parse("2026-10-05T08:00:00Z");

    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    public Clock withZone(ZoneId z) {
      return this;
    }

    public Instant instant() {
      return at;
    }
  }

  /** 测试时钟配置。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @TestConfiguration
  static class TimeConfig {
    @Bean
    @Primary
    MutableClock testClock() {
      return new MutableClock();
    }
  }

  @Autowired MockMvc mvc;
  @Autowired MutableClock clock;
  final JsonMapper json = JsonMapper.builder().build();
  MockHttpSession admin, owner;
  long cid, cid2, ownerId, ownerRole;
  String suffix;

  Map<String, Object> m(Object... pairs) {
    var r = new LinkedHashMap<String, Object>();
    for (int n = 0; n < pairs.length; n += 2) r.put(pairs[n].toString(), pairs[n + 1]);
    return r;
  }

  JsonNode call(MockHttpSession s, String path, String method, Object body, int code)
      throws Exception {
    var b =
        switch (method) {
          case "POST" -> post("/api" + path);
          case "PUT" -> put("/api" + path);
          case "DELETE" -> delete("/api" + path);
          default -> get("/api" + path);
        };
    if (s != null) b.session(s);
    if (!method.equals("GET")) b.with(csrf());
    if (body != null) b.contentType("application/json").content(json.writeValueAsString(body));
    var res = mvc.perform(b).andReturn().getResponse();
    assertEquals(code, res.getStatus(), method + path + res.getContentAsString());
    return json.readTree(res.getContentAsString().isEmpty() ? "{}" : res.getContentAsString());
  }

  MockHttpSession login(String name) throws Exception {
    var r =
        mvc.perform(
                post("/api/auth/login")
                    .with(csrf())
                    .contentType("application/json")
                    .content(json.writeValueAsString(m("username", name, "password", PASSWORD))))
            .andReturn();
    assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
    return (MockHttpSession) r.getRequest().getSession(false);
  }

  long role(String name) throws Exception {
    for (var v : call(admin, "/admin/roles", "GET", null, 200))
      if (v.get("name").asString().contains(name)) return v.get("id").asLong();
    throw new AssertionError(name);
  }

  long createOwner(String code, long dept) throws Exception {
    return call(
            admin,
            "/consignors",
            "POST",
            m(
                "code",
                code,
                "name",
                "TEST " + code,
                "departmentId",
                dept,
                "contactNote",
                "TEST only",
                "enabled",
                true),
            200)
        .get("id")
        .asLong();
  }

  @BeforeEach
  void setup() throws Exception {
    clock.at = Instant.parse("2026-10-05T08:00:00Z");
    suffix = UUID.randomUUID().toString().substring(0, 8);
    admin = login("admin");
    ownerRole = role("Consignor");
    cid = createOwner("A" + suffix, 1);
    cid2 = createOwner("B" + suffix, 1);
    ownerId =
        call(
                admin,
                "/admin/users",
                "POST",
                m(
                    "username",
                    "owner" + suffix,
                    "displayName",
                    "TEST owner",
                    "password",
                    PASSWORD,
                    "roleId",
                    ownerRole,
                    "departmentId",
                    1,
                    "consignorId",
                    cid,
                    "enabled",
                    true),
                200)
            .get("id")
            .asLong();
    owner = login("owner" + suffix);
  }

  Map<String, Object> itemBody(int hold) {
    return m(
        "consignorId",
        cid,
        "name",
        "TEST garment",
        "category",
        "CLOTHING",
        "conditionNote",
        "TEST inspected externally",
        "askingPrice",
        "100.01",
        "minimumPrice",
        "50.00",
        "ownerPercent",
        "60.00",
        "holdDays",
        hold,
        "expiresOn",
        LocalDate.now(clock).plusDays(30).toString());
  }

  JsonNode draft(int hold) throws Exception {
    return call(owner, "/items", "POST", itemBody(hold), 200);
  }

  JsonNode item(long id) throws Exception {
    return call(admin, "/items/" + id, "GET", null, 200).get("item");
  }

  JsonNode act(MockHttpSession s, long id, String action, int code) throws Exception {
    return call(
        s,
        "/items/" + id + "/" + action,
        "POST",
        m(
            "revision",
            item(id).get("revision").asLong(),
            "note",
            "TEST external agreement or inspection",
            "externalReference",
            "TEST-IN-" + UUID.randomUUID()),
        code);
  }

  JsonNode available(int hold) throws Exception {
    var i = draft(hold);
    long id = i.get("id").asLong();
    act(owner, id, "submit", 200);
    act(admin, id, "approve", 200);
    return act(admin, id, "receive", 200);
  }

  Map<String, Object> saleBody(JsonNode i, String ref, String price) {
    return m(
        "itemReference",
        i.get("reference").asString(),
        "revision",
        i.get("revision").asLong(),
        "price",
        price,
        "externalReference",
        ref);
  }

  JsonNode sell(JsonNode i) throws Exception {
    return call(
        admin, "/sales", "POST", saleBody(i, "TEST-SALE-" + UUID.randomUUID(), "100.01"), 200);
  }

  JsonNode bal() throws Exception {
    return call(admin, "/accounts/" + cid, "GET", null, 200).get("balance");
  }

  JsonNode cash(String kind, String amount, int code) throws Exception {
    return call(
        admin,
        "/cash",
        "POST",
        m(
            "consignorId",
            cid,
            "kind",
            kind,
            "amount",
            amount,
            "externalReference",
            "TEST-CASH-" + UUID.randomUUID(),
            "note",
            "TEST actual external transfer"),
        code);
  }

  @Test
  void completeIntakeSalePartialPayoutRefundRecovery() throws Exception {
    var i = available(0);
    var s = sell(i);
    assertEquals(new BigDecimal("60.01"), new BigDecimal(s.get("ownerAmount").asString()));
    assertEquals(
        new BigDecimal("40.00"),
        new BigDecimal(s.get("storeAmount").asString()).setScale(2, RoundingMode.UNNECESSARY));
    cash("PAYOUT", "20.00", 200);
    cash("PAYOUT", "40.01", 200);
    cash("PAYOUT", "0.01", 409);
    call(
        admin,
        "/sales/" + s.get("id").asLong() + "/refund",
        "POST",
        m(
            "externalReference",
            "TEST-REFUND-" + suffix,
            "note",
            "TEST full refund and returned item"),
        200);
    assertEquals("AVAILABLE", item(i.get("id").asLong()).get("status").asString());
    assertEquals(new BigDecimal("-60.01"), new BigDecimal(bal().get("total").asString()));
    cash("RECOVERY", "60.02", 409);
    cash("RECOVERY", "30.00", 200);
    cash("RECOVERY", "30.01", 200);
    assertEquals(0, new BigDecimal(bal().get("total").asString()).signum());
    call(
        admin,
        "/sales/" + s.get("id").asLong() + "/refund",
        "POST",
        m("externalReference", "TEST-RETRY", "note", "TEST"),
        409);
  }

  @Test
  void heldSalesAreNotPayableBeforeMaturity() throws Exception {
    sell(available(7));
    assertEquals(0, new BigDecimal(bal().get("payable").asString()).signum());
    cash("PAYOUT", "1.00", 409);
    clock.at = clock.at.plusSeconds(7 * 86400);
    assertEquals(new BigDecimal("60.01"), new BigDecimal(bal().get("payable").asString()));
    cash("PAYOUT", "60.01", 200);
  }

  @Test
  void refundBeforeMaturityDoesNotCreateFutureAvailableCredit() throws Exception {
    var s = sell(available(7));
    call(
        admin,
        "/sales/" + s.get("id").asLong() + "/refund",
        "POST",
        m("externalReference", "TEST-R" + suffix, "note", "TEST returned"),
        200);
    assertEquals(0, new BigDecimal(bal().get("total").asString()).signum());
    clock.at = clock.at.plusSeconds(8 * 86400);
    assertEquals(0, new BigDecimal(bal().get("payable").asString()).signum());
    cash("PAYOUT", "0.01", 409);
  }

  @Test
  void payoutsReverseOnceWithOriginalAmountAndAuditHistory() throws Exception {
    sell(available(0));
    var p = cash("PAYOUT", "60.01", 200);
    var b =
        m(
            "consignorId",
            cid,
            "kind",
            "REVERSAL",
            "reversalOf",
            p.get("id").asLong(),
            "externalReference",
            "TEST-REV" + suffix,
            "note",
            "TEST bank reversal",
            "amount",
            "1.00");
    call(admin, "/cash", "POST", b, 400);
    b.put("amount", "60.01");
    call(admin, "/cash", "POST", b, 200);
    b.put("externalReference", "TEST-REV-AGAIN" + suffix);
    call(admin, "/cash", "POST", b, 409);
    assertEquals(new BigDecimal("60.01"), new BigDecimal(bal().get("payable").asString()));
  }

  @Test
  void independentReviewFrozenTermsAndStaleRevision() throws Exception {
    var i = draft(0);
    long id = i.get("id").asLong();
    act(owner, id, "submit", 200);
    var v = itemBody(0);
    v.put("revision", i.get("revision").asLong());
    call(owner, "/items/" + id, "PUT", v, 409);
    act(owner, id, "approve", 403);
    act(admin, id, "approve", 200);
    var current = item(id);
    call(
        admin,
        "/items/" + id + "/receive",
        "POST",
        m("revision", 0, "note", "TEST", "externalReference", "TEST-OLD"),
        409);
    act(admin, id, "receive", 200);
    var staffDraft = call(admin, "/items", "POST", itemBody(0), 200);
    act(admin, staffDraft.get("id").asLong(), "submit", 200);
    act(admin, staffDraft.get("id").asLong(), "approve", 409);
    assertTrue(current.get("agreementEvidence").asString().contains("TEST"));
  }

  @Test
  void expiryBlocksSaleAndEvidenceBasedRenewalAllowsIt() throws Exception {
    var i = available(0);
    clock.at = clock.at.plusSeconds(31 * 86400);
    call(admin, "/sales", "POST", saleBody(i, "TEST-EXPIRED", "100"), 409);
    call(
        admin,
        "/items/" + i.get("id").asLong() + "/renew",
        "POST",
        m(
            "revision",
            item(i.get("id").asLong()).get("revision").asLong(),
            "expiresOn",
            LocalDate.now(clock).plusDays(30).toString(),
            "note",
            "TEST owner extension agreement"),
        200);
    sell(item(i.get("id").asLong()));
  }

  @Test
  void floorPricePrecisionFractionalIdsAndHoldDaysRejected() throws Exception {
    var b = itemBody(0);
    b.put("holdDays", 1.5);
    call(owner, "/items", "POST", b, 400);
    b = itemBody(0);
    b.put("ownerPercent", "60.001");
    call(owner, "/items", "POST", b, 400);
    b = itemBody(0);
    b.put("consignorId", 1.5);
    call(owner, "/items", "POST", b, 400);
    var i = available(0);
    call(admin, "/sales", "POST", saleBody(i, "TEST-LOW", "49.99"), 400);
    call(admin, "/sales", "POST", saleBody(i, "TEST-PREC", "100.001"), 400);
    var s = saleBody(i, "TEST-FRAC", "100");
    s.put("revision", 1.5);
    call(admin, "/sales", "POST", s, 400);
    assertEquals("AVAILABLE", item(i.get("id").asLong()).get("status").asString());
  }

  @Test
  void importedBatchRollsBackSalesItemsLedgerAndAudit() throws Exception {
    var i = available(0);
    var k = available(0);
    int prior = call(admin, "/sales", "GET", null, 200).size();
    int ledger = call(admin, "/accounts/" + cid, "GET", null, 200).get("ledger").size();
    int audit = call(admin, "/audit", "GET", null, 200).size();
    call(
        admin,
        "/sales/import",
        "POST",
        List.of(saleBody(i, "TEST-I1" + suffix, "100"), saleBody(k, "TEST-I2" + suffix, "1")),
        400);
    assertEquals(prior, call(admin, "/sales", "GET", null, 200).size());
    assertEquals(ledger, call(admin, "/accounts/" + cid, "GET", null, 200).get("ledger").size());
    assertEquals(audit, call(admin, "/audit", "GET", null, 200).size());
    assertEquals("AVAILABLE", item(i.get("id").asLong()).get("status").asString());
    call(
        admin,
        "/sales/import",
        "POST",
        List.of(
            saleBody(item(i.get("id").asLong()), "TEST-I1" + suffix, "100"),
            saleBody(item(k.get("id").asLong()), "TEST-I2" + suffix, "100")),
        200);
  }

  @Test
  void duplicateReceiptRejectsWithoutAdditionalLedger() throws Exception {
    var i = available(0);
    var s = sell(i);
    var k = available(0);
    call(admin, "/sales", "POST", saleBody(k, s.get("externalReference").asString(), "100"), 409);
    assertEquals("AVAILABLE", item(k.get("id").asLong()).get("status").asString());
    assertEquals(new BigDecimal("60.01"), new BigDecimal(bal().get("total").asString()));
  }

  @Test
  void portalIsolationCannotEscalateRoleBindingOrCallCash() throws Exception {
    call(owner, "/accounts/" + cid2, "GET", null, 403);
    call(owner, "/admin/users", "GET", null, 403);
    call(
        owner,
        "/cash",
        "POST",
        m(
            "consignorId",
            cid,
            "kind",
            "PAYOUT",
            "amount",
            "1",
            "externalReference",
            "TEST-X",
            "note",
            "TEST"),
        403);
    var data =
        m(
            "username",
            "owner" + suffix,
            "displayName",
            "TEST owner",
            "roleId",
            role("Administrator"),
            "departmentId",
            1,
            "consignorId",
            cid,
            "enabled",
            true);
    call(admin, "/admin/users/" + ownerId, "PUT", data, 409);
    data.put("roleId", ownerRole);
    data.put("consignorId", cid2);
    call(admin, "/admin/users/" + ownerId, "PUT", data, 409);
    call(owner, "/auth/me", "GET", null, 200);
    var i =
        call(
            admin,
            "/items",
            "POST",
            m(
                "consignorId",
                cid2,
                "name",
                "TEST other",
                "category",
                "HOME",
                "conditionNote",
                "TEST",
                "askingPrice",
                "50",
                "minimumPrice",
                "20",
                "ownerPercent",
                "50",
                "holdDays",
                0,
                "expiresOn",
                LocalDate.now(clock).plusDays(30).toString()),
            200);
    call(owner, "/items/" + i.get("id").asLong(), "GET", null, 403);
  }

  @Test
  void revokedProcessPermissionCannotDeleteExistingStaffDraft() throws Exception {
    var r =
        call(
            admin,
            "/admin/roles",
            "POST",
            m(
                "name",
                "TEST limited " + suffix,
                "scope",
                "DEPARTMENT",
                "permissions",
                Set.of("read", "process")),
            200);
    call(
        admin,
        "/admin/users",
        "POST",
        m(
            "username",
            "staff" + suffix,
            "displayName",
            "TEST staff",
            "password",
            PASSWORD,
            "roleId",
            r.get("id").asLong(),
            "departmentId",
            1,
            "enabled",
            true),
        200);
    var s = login("staff" + suffix);
    var i = call(s, "/items", "POST", itemBody(0), 200);
    call(
        admin,
        "/admin/roles/" + r.get("id").asLong(),
        "PUT",
        m("name", "TEST limited " + suffix, "scope", "DEPARTMENT", "permissions", Set.of("read")),
        200);
    call(
        s,
        "/items/" + i.get("id").asLong() + "?revision=" + i.get("revision").asLong(),
        "DELETE",
        null,
        403);
    assertEquals("DRAFT", item(i.get("id").asLong()).get("status").asString());
  }

  @Test
  void exportNeutralizesFormulaTextWhileNegativeLedgerAmountsRemainNumeric() throws Exception {
    var b = itemBody(0);
    b.put("name", "=TEST, \"quoted\"\nnext line");
    var i = call(owner, "/items", "POST", b, 200);
    act(owner, i.get("id").asLong(), "submit", 200);
    act(admin, i.get("id").asLong(), "approve", 200);
    i = act(admin, i.get("id").asLong(), "receive", 200);
    var sale = sell(i);
    cash("PAYOUT", "60.01", 200);
    var csv = mvc.perform(get("/api/export/sales").session(admin)).andReturn().getResponse();
    assertEquals(200, csv.getStatus());
    assertTrue(csv.getContentAsString().contains("'=TEST, \"\"quoted\"\"\nnext line"));
    var ledger = mvc.perform(get("/api/export/ledger").session(admin)).andReturn().getResponse();
    assertTrue(ledger.getContentAsString().contains("\"-60.01\""));
    assertFalse(ledger.getContentAsString().contains("'-60.01"));
    call(owner, "/export/sales", "GET", null, 403);
  }

  @Test
  void referencedDepartmentsAndCurrencyAreImmutable() throws Exception {
    available(0);
    long d =
        call(admin, "/admin/departments", "POST", m("name", "TEST D" + suffix), 200)
            .get("id")
            .asLong();
    var c = call(admin, "/consignors", "GET", null, 200);
    JsonNode original = null;
    for (var x : c) if (x.get("id").asLong() == cid) original = x;
    call(
        admin,
        "/consignors/" + cid,
        "PUT",
        m(
            "revision",
            original.get("revision").asLong(),
            "code",
            original.get("code").asString(),
            "name",
            "TEST moved",
            "contactNote",
            "TEST",
            "enabled",
            true,
            "departmentId",
            d),
        409);
    for (var s : call(admin, "/admin/settings", "GET", null, 200))
      if (s.get("code").asString().equals("currency"))
        call(admin, "/admin/settings/" + s.get("id").asLong(), "PUT", m("value", "USD"), 409);
    call(admin, "/consignors/" + cid, "DELETE", null, 409);
  }

  @Test
  void disabledOwnerSessionDeniedImmediatelyAndLastAdminProtected() throws Exception {
    var c = call(admin, "/consignors", "GET", null, 200);
    for (var x : c)
      if (x.get("id").asLong() == cid)
        call(
            admin,
            "/consignors/" + cid,
            "PUT",
            m(
                "revision",
                x.get("revision").asLong(),
                "code",
                x.get("code").asString(),
                "name",
                "TEST disabled",
                "contactNote",
                "TEST",
                "enabled",
                false,
                "departmentId",
                1),
            200);
    call(owner, "/items", "GET", null, 403);
    long aid = call(admin, "/auth/me", "GET", null, 200).get("id").asLong();
    call(admin, "/admin/users/" + aid, "DELETE", null, 409);
    call(admin, "/auth/me", "GET", null, 200);
  }

  @Test
  void collectWithoutSaleAndDeleteOnlyNeverSubmittedDraft() throws Exception {
    var d = draft(0);
    call(
        owner,
        "/items/" + d.get("id").asLong() + "?revision=" + d.get("revision").asLong(),
        "DELETE",
        null,
        200);
    var i = available(0);
    act(admin, i.get("id").asLong(), "collect", 200);
    call(
        admin,
        "/sales",
        "POST",
        saleBody(item(i.get("id").asLong()), "TEST-COLLECTED", "100"),
        409);
    var k = draft(0);
    act(owner, k.get("id").asLong(), "submit", 200);
    act(admin, k.get("id").asLong(), "reject", 200);
    var latest = item(k.get("id").asLong());
    call(
        owner,
        "/items/" + latest.get("id").asLong() + "?revision=" + latest.get("revision").asLong(),
        "DELETE",
        null,
        409);
  }

  @Test
  void anonymousAndMissingCsrfCannotWrite() throws Exception {
    call(null, "/items", "GET", null, 401);
    var res =
        mvc.perform(
                post("/api/items")
                    .session(owner)
                    .contentType("application/json")
                    .content(json.writeValueAsString(itemBody(0))))
            .andReturn();
    assertEquals(403, res.getResponse().getStatus());
  }
}
