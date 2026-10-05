-- Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2

CREATE TABLE department (
  id bigint AUTO_INCREMENT PRIMARY KEY,
  name varchar(120) NOT NULL UNIQUE
);

CREATE TABLE access_role (
  id bigint AUTO_INCREMENT PRIMARY KEY,
  name varchar(120) NOT NULL UNIQUE,
  scope varchar(20) NOT NULL
);

CREATE TABLE permission (
  id bigint AUTO_INCREMENT PRIMARY KEY,
  code varchar(60) NOT NULL UNIQUE,
  name varchar(120) NOT NULL
);

CREATE TABLE role_permission (role_id bigint NOT NULL, permission_code varchar(60) NOT NULL, PRIMARY KEY(role_id, permission_code), FOREIGN KEY(role_id) REFERENCES access_role(id), FOREIGN KEY(permission_code) REFERENCES permission(code));

CREATE TABLE nav_menu (
  id bigint AUTO_INCREMENT PRIMARY KEY,
  code varchar(60) NOT NULL UNIQUE,
  name varchar(120) NOT NULL,
  name_en varchar(120) NOT NULL,
  permission_code varchar(60) NOT NULL,
  position int NOT NULL,
  enabled boolean NOT NULL
);

CREATE TABLE account (
  id bigint AUTO_INCREMENT PRIMARY KEY,
  username varchar(60) NOT NULL UNIQUE,
  display_name varchar(120) NOT NULL,
  password_hash varchar(100) NOT NULL,
  role_id bigint NOT NULL,
  department_id bigint NOT NULL,
  enabled boolean NOT NULL,
  FOREIGN KEY (role_id) REFERENCES access_role(id),
  FOREIGN KEY (department_id) REFERENCES department(id)
);

CREATE TABLE audit_event (
  id bigint AUTO_INCREMENT PRIMARY KEY,
  actor varchar(60) NOT NULL,
  action varchar(120) NOT NULL,
  object_id varchar(80) NOT NULL,
  department_id bigint NOT NULL,
  created_at timestamp(6) NOT NULL
);

CREATE TABLE system_setting (
  id bigint AUTO_INCREMENT PRIMARY KEY,
  code varchar(60) NOT NULL UNIQUE,
  parameter_value varchar(6000) NOT NULL
);

CREATE TABLE dictionary_entry (
  id bigint AUTO_INCREMENT PRIMARY KEY,
  type varchar(60) NOT NULL,
  code varchar(60) NOT NULL,
  name varchar(120) NOT NULL,
  name_en varchar(120) NOT NULL,
enabled boolean NOT NULL DEFAULT TRUE,
  UNIQUE (type, code)
);






CREATE TABLE consignor (
 id bigint AUTO_INCREMENT PRIMARY KEY,
 code varchar(60) NOT NULL UNIQUE,
 name varchar(160) NOT NULL,
 department_id bigint NOT NULL,
 contact_note varchar(500) NOT NULL,
 enabled boolean NOT NULL,
 revision bigint NOT NULL,
 FOREIGN KEY(department_id) REFERENCES department(id)
);

CREATE TABLE consigned_item (
 id bigint AUTO_INCREMENT PRIMARY KEY,
 reference varchar(60) NOT NULL UNIQUE,
 consignor_id bigint NOT NULL,
 department_id bigint NOT NULL,
 name varchar(160) NOT NULL,
 category varchar(60) NOT NULL,
 condition_note varchar(1000) NOT NULL,
 asking_price decimal(18,2) NOT NULL,
 minimum_price decimal(18,2) NOT NULL,
 owner_percent decimal(5,2) NOT NULL,
 hold_days int NOT NULL,
 expires_on date NOT NULL,
 status varchar(20) NOT NULL,
 creator_id bigint NOT NULL,
 agreement_evidence varchar(500) NOT NULL,
 intake_reference varchar(100) UNIQUE,
 received_at timestamp(6),
 revision bigint NOT NULL,
 created_at timestamp(6) NOT NULL,
 FOREIGN KEY(consignor_id) REFERENCES consignor(id),
 FOREIGN KEY(department_id) REFERENCES department(id),
 FOREIGN KEY(creator_id) REFERENCES account(id),
 CHECK(asking_price >= minimum_price AND minimum_price > 0 AND owner_percent > 0 AND owner_percent <= 100 AND hold_days >= 0 AND hold_days <= 90),
 INDEX ix_item_owner_status(consignor_id,status),
 INDEX ix_item_expiry(expires_on)
);

CREATE TABLE item_event (
 id bigint AUTO_INCREMENT PRIMARY KEY,
 item_id bigint NOT NULL,
 action varchar(60) NOT NULL,
 actor varchar(60) NOT NULL,
 note varchar(1500) NOT NULL,
 created_at timestamp(6) NOT NULL,
 FOREIGN KEY(item_id) REFERENCES consigned_item(id)
);

CREATE TABLE consignment_sale (
 id bigint AUTO_INCREMENT PRIMARY KEY,
 item_id bigint NOT NULL,
 consignor_id bigint NOT NULL,
 department_id bigint NOT NULL,
 external_reference varchar(100) NOT NULL UNIQUE,
 item_name varchar(160) NOT NULL,
 price decimal(18,2) NOT NULL,
 owner_percent decimal(5,2) NOT NULL,
 owner_amount decimal(18,2) NOT NULL,
 store_amount decimal(18,2) NOT NULL,
 sold_at timestamp(6) NOT NULL,
 available_on date NOT NULL,
 status varchar(20) NOT NULL,
 refund_reference varchar(100) UNIQUE,
 refund_reason varchar(1000) NOT NULL,
 refunded_at timestamp(6),
 FOREIGN KEY(item_id) REFERENCES consigned_item(id),
 FOREIGN KEY(consignor_id) REFERENCES consignor(id),
 FOREIGN KEY(department_id) REFERENCES department(id),
 CHECK(price > 0 AND owner_amount >= 0 AND store_amount >= 0 AND owner_amount + store_amount = price)
);

CREATE TABLE cash_movement (
 id bigint AUTO_INCREMENT PRIMARY KEY,
 consignor_id bigint NOT NULL,
 department_id bigint NOT NULL,
 kind varchar(20) NOT NULL,
 external_reference varchar(100) NOT NULL UNIQUE,
 amount decimal(18,2) NOT NULL,
 reversal_of bigint UNIQUE,
 note varchar(1000) NOT NULL,
 actor varchar(60) NOT NULL,
 created_at timestamp(6) NOT NULL,
 FOREIGN KEY(consignor_id) REFERENCES consignor(id),
 FOREIGN KEY(department_id) REFERENCES department(id),
 FOREIGN KEY(reversal_of) REFERENCES cash_movement(id)
);

CREATE TABLE ledger_entry (
 id bigint AUTO_INCREMENT PRIMARY KEY,
 consignor_id bigint NOT NULL,
 department_id bigint NOT NULL,
 sale_id bigint,
 cash_id bigint,
 kind varchar(20) NOT NULL,
 amount decimal(18,2) NOT NULL,
 available_on date NOT NULL,
 created_at timestamp(6) NOT NULL,
 FOREIGN KEY(consignor_id) REFERENCES consignor(id),
 FOREIGN KEY(department_id) REFERENCES department(id),
 FOREIGN KEY(sale_id) REFERENCES consignment_sale(id),
 FOREIGN KEY(cash_id) REFERENCES cash_movement(id),
 INDEX ix_ledger_owner_date(consignor_id,available_on)
);

ALTER TABLE account ADD consignor_id bigint;
ALTER TABLE account ADD CONSTRAINT fk_account_consignor FOREIGN KEY(consignor_id) REFERENCES consignor(id);
