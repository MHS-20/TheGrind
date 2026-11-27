CREATE TABLE parties (id NUMBER PRIMARY KEY, name VARCHAR2(40) NOT NULL, country VARCHAR2(20));
CREATE TABLE districts (id NUMBER PRIMARY KEY, description VARCHAR2(40), seats NUMBER NOT NULL);
CREATE TABLE candidates (
    id          NUMBER PRIMARY KEY,
    first_name  VARCHAR2(40),
    last_name   VARCHAR2(40),
    party       NUMBER REFERENCES parties,
    district    NUMBER REFERENCES districts,
    preferences NUMBER NOT NULL
);
CREATE TABLE votes (
    district NUMBER REFERENCES districts,
    party    NUMBER REFERENCES parties,
    votes    NUMBER NOT NULL,
    PRIMARY KEY (district, party)
);

INSERT INTO districts VALUES (1, 'North-East', 5);
INSERT INTO districts VALUES (2, 'Islands', 3);
INSERT INTO parties VALUES (1, 'Blue', 'IT');
INSERT INTO parties VALUES (2, 'Green', 'IT');
INSERT INTO parties VALUES (3, 'Red', 'IT');
-- district 1: 5000 votes, 5 seats -> Blue 2000 = 2 seats, Green 2400 = 2.4 -> 2, Red 600 = 0.6 -> 1
INSERT INTO votes VALUES (1, 1, 2000);
INSERT INTO votes VALUES (1, 2, 2400);
INSERT INTO votes VALUES (1, 3, 600);
INSERT INTO votes VALUES (2, 1, 900);
INSERT INTO candidates VALUES (1, 'Ada', 'Lovelace', 1, 1, 900);
INSERT INTO candidates VALUES (2, 'Bob', 'Brown',    1, 1, 300);
INSERT INTO candidates VALUES (3, 'Cat', 'Black',    1, 1, 700);
INSERT INTO candidates VALUES (4, 'Dan', 'Green',    2, 1, 1000);
INSERT INTO candidates VALUES (5, 'Eve', 'White',    2, 1, 1200);
INSERT INTO candidates VALUES (6, 'Fay', 'Grey',     2, 1, 100);
INSERT INTO candidates VALUES (7, 'Gus', 'Red',      3, 1, 400);
INSERT INTO candidates VALUES (8, 'Hal', 'Rose',     1, 2, 500);
COMMIT;
