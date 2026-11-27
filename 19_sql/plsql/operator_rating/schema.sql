CREATE TABLE customers (id NUMBER PRIMARY KEY, name VARCHAR2(40) NOT NULL, contract VARCHAR2(20));
CREATE TABLE operators (id NUMBER PRIMARY KEY, name VARCHAR2(40) NOT NULL, op_level NUMBER);
CREATE TABLE problems (
    code        VARCHAR2(10) PRIMARY KEY,
    description VARCHAR2(80),
    difficulty  NUMBER NOT NULL CHECK (difficulty > 0 AND difficulty <= 1)
);
CREATE TABLE calls (
    id       NUMBER PRIMARY KEY,
    customer NUMBER REFERENCES customers,
    operator NUMBER REFERENCES operators,
    problem  VARCHAR2(10) REFERENCES problems,
    opened   DATE NOT NULL,
    result   NUMBER NOT NULL CHECK (result IN (-1, 0, 1))   -- 0: still open
);
CREATE TABLE operations (
    call_id     NUMBER REFERENCES calls,
    done_at     DATE NOT NULL,
    minutes     NUMBER NOT NULL,
    description VARCHAR2(80)
);

INSERT INTO customers VALUES (1, 'Acme', 'business');
INSERT INTO customers VALUES (2, 'Ada',  'home');
INSERT INTO operators VALUES (1, 'Rita', 2);
INSERT INTO operators VALUES (2, 'Sam',  1);
INSERT INTO operators VALUES (3, 'Tom',  1);
INSERT INTO problems VALUES ('NET',  'No connection',  0.5);
INSERT INTO problems VALUES ('BILL', 'Billing',        0.2);
INSERT INTO problems VALUES ('HW',   'Broken router',  1);

-- Rita: +30*0.5 = 15, -10*0.2 = 2          -> (15-2)/(15+2)  =  0.765
INSERT INTO calls VALUES (1, 1, 1, 'NET',  DATE '2026-11-02', 1);
INSERT INTO operations VALUES (1, DATE '2026-11-02', 20, 'diagnosis');
INSERT INTO operations VALUES (1, DATE '2026-11-03', 10, 'fix');
INSERT INTO calls VALUES (2, 2, 1, 'BILL', DATE '2026-11-04', -1);
INSERT INTO operations VALUES (2, DATE '2026-11-04', 10, 'refund refused');
-- Sam: +20*0.2 = 4, -40*1 = 40              -> (4-40)/(4+40)  = -0.818
INSERT INTO calls VALUES (3, 1, 2, 'BILL', DATE '2026-11-05', 1);
INSERT INTO operations VALUES (3, DATE '2026-11-05', 20, 'explained');
INSERT INTO calls VALUES (4, 2, 2, 'HW',   DATE '2026-11-06', -1);
INSERT INTO operations VALUES (4, DATE '2026-11-06', 25, 'remote reset');
INSERT INTO operations VALUES (4, DATE '2026-11-07', 15, 'gave up');
-- Tom: +10*1 = 10, -20*0.5 = 10, open call ignored -> 0, not negative
INSERT INTO calls VALUES (5, 1, 3, 'HW',   DATE '2026-11-08', 1);
INSERT INTO operations VALUES (5, DATE '2026-11-08', 10, 'replaced');
INSERT INTO calls VALUES (6, 2, 3, 'NET',  DATE '2026-11-09', -1);
INSERT INTO operations VALUES (6, DATE '2026-11-09', 20, 'escalated');
INSERT INTO calls VALUES (7, 2, 3, 'NET',  DATE '2026-11-10', 0);
INSERT INTO operations VALUES (7, DATE '2026-11-10', 90, 'in progress');
-- Rita, before the period: a bad call that must not count
INSERT INTO calls VALUES (8, 1, 1, 'HW',   DATE '2026-10-01', -1);
INSERT INTO operations VALUES (8, DATE '2026-10-01', 100, 'failed');
COMMIT;
