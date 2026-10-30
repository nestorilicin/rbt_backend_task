-- Drops the databases and creates them again, empty, with all tables.
-- WARNING: all existing data in both databases is deleted.

DROP DATABASE IF EXISTS budget;
CREATE DATABASE budget CHARACTER SET utf8mb4;

DROP DATABASE IF EXISTS budget_test;
CREATE DATABASE budget_test CHARACTER SET utf8mb4;

-- ---------- budget (main database) ----------
USE budget;

CREATE TABLE category (
  id    BIGINT AUTO_INCREMENT PRIMARY KEY,
  name  VARCHAR(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_ci NOT NULL,
  color CHAR(7) NOT NULL,
  CONSTRAINT uq_category_name UNIQUE (name),
  CONSTRAINT ck_category_color CHECK (color REGEXP '^#[0-9A-Fa-f]{6}$')
);

CREATE TABLE app_transaction (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  type        VARCHAR(7)   NOT NULL,
  amount      DECIMAL(9,2) NOT NULL,
  date        DATE         NOT NULL,
  description VARCHAR(200) NULL,
  category_id BIGINT       NOT NULL,
  CONSTRAINT ck_tx_type   CHECK (type IN ('INCOME','EXPENSE')),
  CONSTRAINT ck_tx_amount CHECK (amount > 0 AND amount <= 1000000),
  CONSTRAINT fk_tx_cat FOREIGN KEY (category_id) REFERENCES category(id) ON DELETE RESTRICT,
  INDEX ix_tx_date_id (date, id),
  INDEX ix_tx_cat_date (category_id, date)
);

CREATE TABLE budget (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  month       DATE         NOT NULL,   -- always the first day of the month
  category_id BIGINT       NOT NULL,
  amount      DECIMAL(9,2) NOT NULL,
  CONSTRAINT ck_budget_amount CHECK (amount > 0 AND amount <= 1000000),
  CONSTRAINT uq_budget UNIQUE (category_id, month),
  CONSTRAINT fk_budget_cat FOREIGN KEY (category_id) REFERENCES category(id) ON DELETE CASCADE
);

-- ---------- budget_test (used only by automated tests) ----------
USE budget_test;

CREATE TABLE category (
  id    BIGINT AUTO_INCREMENT PRIMARY KEY,
  name  VARCHAR(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_ci NOT NULL,
  color CHAR(7) NOT NULL,
  CONSTRAINT uq_category_name UNIQUE (name),
  CONSTRAINT ck_category_color CHECK (color REGEXP '^#[0-9A-Fa-f]{6}$')
);

CREATE TABLE app_transaction (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  type        VARCHAR(7)   NOT NULL,
  amount      DECIMAL(9,2) NOT NULL,
  date        DATE         NOT NULL,
  description VARCHAR(200) NULL,
  category_id BIGINT       NOT NULL,
  CONSTRAINT ck_tx_type   CHECK (type IN ('INCOME','EXPENSE')),
  CONSTRAINT ck_tx_amount CHECK (amount > 0 AND amount <= 1000000),
  CONSTRAINT fk_tx_cat FOREIGN KEY (category_id) REFERENCES category(id) ON DELETE RESTRICT,
  INDEX ix_tx_date_id (date, id),
  INDEX ix_tx_cat_date (category_id, date)
);

CREATE TABLE budget (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  month       DATE         NOT NULL,
  category_id BIGINT       NOT NULL,
  amount      DECIMAL(9,2) NOT NULL,
  CONSTRAINT ck_budget_amount CHECK (amount > 0 AND amount <= 1000000),
  CONSTRAINT uq_budget UNIQUE (category_id, month),
  CONSTRAINT fk_budget_cat FOREIGN KEY (category_id) REFERENCES category(id) ON DELETE CASCADE
);