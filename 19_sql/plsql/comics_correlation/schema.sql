CREATE TABLE comics (name VARCHAR2(40) PRIMARY KEY, artist VARCHAR2(40), publisher VARCHAR2(40), price NUMBER);
CREATE TABLE issues (
    comic VARCHAR2(40) REFERENCES comics,
    issue NUMBER,
    year  NUMBER,
    pages NUMBER,
    PRIMARY KEY (comic, issue)
);
CREATE TABLE adventures (
    comic       VARCHAR2(40),
    issue       NUMBER,
    title       VARCHAR2(60),
    description VARCHAR2(200),
    pages       NUMBER,
    PRIMARY KEY (comic, issue, title),
    FOREIGN KEY (comic, issue) REFERENCES issues
);
CREATE TABLE characters (name VARCHAR2(40) PRIMARY KEY, description VARCHAR2(80));
CREATE TABLE appears_in (
    character VARCHAR2(40) REFERENCES characters,
    comic     VARCHAR2(40),
    issue     NUMBER,
    title     VARCHAR2(60),
    panels    NUMBER,
    PRIMARY KEY (character, comic, issue, title),
    FOREIGN KEY (comic, issue, title) REFERENCES adventures
);

INSERT INTO comics VALUES ('Mouse Tales', 'A', 'P', 3);
INSERT INTO comics VALUES ('Duck City',   'B', 'P', 3);
INSERT INTO issues VALUES ('Mouse Tales', 1, 2025, 100);
INSERT INTO issues VALUES ('Mouse Tales', 2, 2025, 100);
INSERT INTO issues VALUES ('Duck City',   1, 2025, 100);
INSERT INTO issues VALUES ('Duck City',   2, 2026, 100);
INSERT INTO adventures VALUES ('Mouse Tales', 1, 'The Lost Map',   NULL, 30);
INSERT INTO adventures VALUES ('Mouse Tales', 2, 'Moon Trip',      NULL, 30);
INSERT INTO adventures VALUES ('Duck City',   1, 'Money Bin',      NULL, 30);
INSERT INTO adventures VALUES ('Duck City',   1, 'Gold Rush',      NULL, 30);
INSERT INTO adventures VALUES ('Duck City',   2, 'Crossover',      NULL, 30);
INSERT INTO characters VALUES ('Mouse', NULL);
INSERT INTO characters VALUES ('Dog',   NULL);
INSERT INTO characters VALUES ('Duck',  NULL);
INSERT INTO characters VALUES ('Uncle', NULL);
-- Mouse and Dog appear in Mouse Tales
INSERT INTO appears_in VALUES ('Mouse', 'Mouse Tales', 1, 'The Lost Map', 40);
INSERT INTO appears_in VALUES ('Mouse', 'Mouse Tales', 2, 'Moon Trip',    35);
INSERT INTO appears_in VALUES ('Dog',   'Mouse Tales', 1, 'The Lost Map', 20);
-- in Duck City: Mouse appears in 2 adventures, Dog in 1, Duck and Uncle are not in Mouse Tales
INSERT INTO appears_in VALUES ('Duck',  'Duck City', 1, 'Money Bin', 50);
INSERT INTO appears_in VALUES ('Uncle', 'Duck City', 1, 'Money Bin', 30);
INSERT INTO appears_in VALUES ('Mouse', 'Duck City', 1, 'Gold Rush', 10);
INSERT INTO appears_in VALUES ('Mouse', 'Duck City', 2, 'Crossover', 12);
INSERT INTO appears_in VALUES ('Dog',   'Duck City', 2, 'Crossover', 5);
COMMIT;
