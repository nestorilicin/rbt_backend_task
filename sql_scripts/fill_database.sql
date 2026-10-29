USE budget;

-- Workbench "safe update mode" ne dozvoljava DELETE bez WHERE
SET SQL_SAFE_UPDATES = 0;

DELETE FROM budget;
DELETE FROM app_transaction;
DELETE FROM category;
ALTER TABLE budget          AUTO_INCREMENT = 1;
ALTER TABLE app_transaction AUTO_INCREMENT = 1;
ALTER TABLE category        AUTO_INCREMENT = 1;

-- ---------- Kategorije ----------
INSERT INTO category (name, color) VALUES
  ('Food',       '#4CAF50'),
  ('Eating out', '#FF9800'),
  ('Transport',  '#2196F3'),
  ('Rent',       '#9C27B0'),
  ('Salary',     '#009688'),
  ('Fun',        '#E91E63'),
  ('Other',      '#607D8B'),
  ('Health',     '#F44336'),   -- samo budzet, bez transakcija
  ('Gifts',      '#FFC107');   -- prazna, moze da se obrise

SET @food    = (SELECT id FROM category WHERE name = 'Food');
SET @eating  = (SELECT id FROM category WHERE name = 'Eating out');
SET @transp  = (SELECT id FROM category WHERE name = 'Transport');
SET @rent    = (SELECT id FROM category WHERE name = 'Rent');
SET @salary  = (SELECT id FROM category WHERE name = 'Salary');
SET @fun     = (SELECT id FROM category WHERE name = 'Fun');
SET @other   = (SELECT id FROM category WHERE name = 'Other');
SET @health  = (SELECT id FROM category WHERE name = 'Health');

-- ---------- Transakcije: avgust 2026 (negativan balans) ----------
INSERT INTO app_transaction (type, amount, date, description, category_id) VALUES
  ('INCOME',  1500.00, '2026-08-01', 'August salary', @salary),
  ('EXPENSE',  550.00, '2026-08-02', 'Rent',          @rent),
  ('EXPENSE',  180.00, '2026-08-14', 'Groceries',     @food),
  ('EXPENSE',   60.00, '2026-08-22', 'Concert',       @fun),
  ('EXPENSE', 1200.00, '2026-08-25', 'New laptop',    @other);

-- ---------- Septembar 2026 ----------
INSERT INTO app_transaction (type, amount, date, description, category_id) VALUES
  ('INCOME',  1500.00, '2026-09-01', 'September salary',      @salary),
  ('EXPENSE',  550.00, '2026-09-02', 'Rent',                  @rent),
  ('EXPENSE',  210.40, '2026-09-12', 'Groceries',             @food),
  ('EXPENSE',   40.00, '2026-09-15', 'Monthly bus ticket',    @transp),
  ('EXPENSE',   76.20, '2026-09-20', 'Dinner',                @eating),
  ('EXPENSE',   22.00, '2026-09-30', 'Last day of September', @food);

-- ---------- Oktobar 2026 (glavni mesec za demo) ----------
INSERT INTO app_transaction (type, amount, date, description, category_id) VALUES
  ('INCOME',  1500.00, '2026-10-01', 'October salary',       @salary),
  ('EXPENSE',  550.00, '2026-10-02', 'Rent',                 @rent),
  ('EXPENSE',   45.30, '2026-10-03', 'Groceries',            @food),
  ('EXPENSE',   25.00, '2026-10-05', 'Monthly bus ticket',   @transp),
  ('EXPENSE',   12.50, '2026-10-08', 'Lunch',                @eating),
  ('EXPENSE',   28.00, '2026-10-08', 'Dinner with friends',  @eating),   -- isti datum kao Lunch
  ('INCOME',   300.00, '2026-10-09', NULL,                   @other),    -- bez opisa
  ('EXPENSE',   62.10, '2026-10-10', 'Groceries',            @food),
  ('EXPENSE',   19.99, '2026-10-12', 'Cinema',               @fun),
  ('EXPENSE',   49.90, '2026-10-14', '50% off jacket',       @other),    -- % u opisu
  ('EXPENSE',   15.90, '2026-10-15', 'Lunch',                @eating),
  ('EXPENSE',   38.75, '2026-10-17', 'Groceries',            @food),
  ('EXPENSE',   18.40, '2026-10-19', 'Taxi',                 @transp),
  ('EXPENSE',   64.50, '2026-10-22', 'Birthday dinner',      @eating),
  ('EXPENSE',  120.00, '2026-10-24', 'Big shopping',         @food),
  ('EXPENSE',   35.00, '2026-10-26', 'Concert',              @fun);

-- ---------- Novembar (granica meseca) ----------
INSERT INTO app_transaction (type, amount, date, description, category_id) VALUES
  ('EXPENSE', 9.99, '2026-11-01', 'First day of November', @food);

-- ---------- Budzeti ----------
INSERT INTO budget (month, category_id, amount) VALUES
  ('2026-09-01', @food,   250.00),
  ('2026-09-01', @rent,   600.00),
  ('2026-10-01', @food,   300.00),
  ('2026-10-01', @eating, 100.00),   -- prekoracen
  ('2026-10-01', @transp,  80.00),
  ('2026-10-01', @rent,   600.00),
  ('2026-10-01', @fun,     50.00),   -- prekoracen
  ('2026-10-01', @health,  40.00);   -- samo budzet, bez troskova

SET SQL_SAFE_UPDATES = 1;

SELECT 'category' AS tabela, COUNT(*) AS redova FROM category
UNION ALL SELECT 'app_transaction', COUNT(*) FROM app_transaction
UNION ALL SELECT 'budget', COUNT(*) FROM budget;