CREATE TABLE books (id VARCHAR2(10) PRIMARY KEY, title VARCHAR2(80), author VARCHAR2(40));
CREATE TABLE brackets (
    book     VARCHAR2(10) REFERENCES books,
    cum_from NUMBER NOT NULL,
    cum_to   NUMBER NOT NULL,
    percent  NUMBER NOT NULL,
    PRIMARY KEY (book, cum_from)
);
CREATE TABLE sales (
    book     VARCHAR2(10) REFERENCES books,
    sold_on  DATE NOT NULL,
    country  VARCHAR2(20),
    quantity NUMBER,
    amount   NUMBER NOT NULL
);

INSERT INTO books VALUES ('AS45', 'Databases in Practice', 'Ada');
INSERT INTO brackets VALUES ('AS45',      0,  30000,  5);
INSERT INTO brackets VALUES ('AS45',  30001, 175000, 15);
INSERT INTO brackets VALUES ('AS45', 175001, 300000, 20);
INSERT INTO brackets VALUES ('AS45', 300001, 500000, 12);
-- 2012: 50,000 in total
INSERT INTO sales VALUES ('AS45', DATE '2012-02-10', 'IT', 1000, 20000);
INSERT INTO sales VALUES ('AS45', DATE '2012-07-01', 'FR',  900, 18000);
INSERT INTO sales VALUES ('AS45', DATE '2012-11-30', 'DE',  600, 12000);
INSERT INTO sales VALUES ('AS45', DATE '2013-01-02', 'IT',  100,  2000);
COMMIT;
