CREATE TABLE regions (name VARCHAR2(40) PRIMARY KEY, population NUMBER);
CREATE TABLE districts (name VARCHAR2(40) PRIMARY KEY, description VARCHAR2(80));
CREATE TABLE cities (
    name       VARCHAR2(40),
    region     VARCHAR2(40) REFERENCES regions,
    population NUMBER NOT NULL,
    district   VARCHAR2(40) REFERENCES districts,
    PRIMARY KEY (name, region)
);
CREATE TABLE parameters (
    city           VARCHAR2(40),
    region         VARCHAR2(40),
    parameter      VARCHAR2(40),
    parameter_type VARCHAR2(20) NOT NULL,
    description    VARCHAR2(80),
    value          NUMBER NOT NULL,
    PRIMARY KEY (city, region, parameter),
    FOREIGN KEY (city, region) REFERENCES cities
);
CREATE TABLE livability (
    city   VARCHAR2(40),
    region VARCHAR2(40),
    value  NUMBER,
    PRIMARY KEY (city, region),
    FOREIGN KEY (city, region) REFERENCES cities
);

INSERT INTO regions VALUES ('Emilia', 4400000);
INSERT INTO regions VALUES ('Liguria', 1500000);
INSERT INTO districts VALUES ('Centre', NULL);

-- 12 cities in Emilia: pollution = (PM10 + NO2) / 2, green area in m2, cultural centres
-- C11 and C12 are the two most polluted and must be excluded before ranking
INSERT INTO cities
SELECT 'C' || level, 'Emilia', 10000 * level, 'Centre' FROM dual CONNECT BY level <= 12;
INSERT INTO parameters
SELECT 'C' || level, 'Emilia', 'PM10', 'Pollution', NULL, 20 + level FROM dual CONNECT BY level <= 12;
INSERT INTO parameters
SELECT 'C' || level, 'Emilia', 'NO2', 'Pollution', NULL, 30 + level FROM dual CONNECT BY level <= 12;
INSERT INTO parameters
SELECT 'C' || level, 'Emilia', 'GreenArea', 'Environment', 'm2', 1000000 + 400000 * MOD(level * 7, 12)
FROM dual CONNECT BY level <= 12;
INSERT INTO parameters
SELECT 'C' || level, 'Emilia', 'CulturalCentres', 'Services', NULL, MOD(level * 5, 9)
FROM dual CONNECT BY level <= 12;
-- C12 would rank first if pollution were ignored
UPDATE parameters SET value = 90000000 WHERE city = 'C12' AND parameter = 'GreenArea';
-- a city in another region
INSERT INTO cities VALUES ('Genova', 'Liguria', 560000, 'Centre');
INSERT INTO parameters VALUES ('Genova', 'Liguria', 'PM10', 'Pollution', NULL, 1);
COMMIT;
