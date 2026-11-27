CREATE TABLE patients (
    tax_code   VARCHAR2(16) PRIMARY KEY,
    first_name VARCHAR2(40) NOT NULL,
    last_name  VARCHAR2(40) NOT NULL,
    sex        CHAR(1),
    birth_date DATE
);
CREATE TABLE treatments (code VARCHAR2(10) PRIMARY KEY, name VARCHAR2(60) NOT NULL, cost NUMBER NOT NULL);
CREATE TABLE stays (
    patient    VARCHAR2(16) REFERENCES patients,
    start_date DATE NOT NULL,
    end_date   DATE,                                 -- NULL: still in hospital
    PRIMARY KEY (patient, start_date)
);
CREATE TABLE stay_treatments (
    patient    VARCHAR2(16),
    stay_start DATE,
    treatment  VARCHAR2(10) REFERENCES treatments,
    FOREIGN KEY (patient, stay_start) REFERENCES stays
);

INSERT INTO patients VALUES ('RSSMRA80A01H501U', 'Mario', 'Rossi',   'M', DATE '1980-01-01');
INSERT INTO patients VALUES ('BNCLRA75B41F205X', 'Laura', 'Bianchi', 'F', DATE '1975-02-01');
INSERT INTO patients VALUES ('VRDGPP90C10A944K', 'Giuseppe', 'Verdi', 'M', DATE '1990-03-10');
INSERT INTO treatments VALUES ('XR',  'X-ray',       50);
INSERT INTO treatments VALUES ('MRI', 'MRI scan',   300);
INSERT INTO treatments VALUES ('SRG', 'Surgery',   1200);
INSERT INTO treatments VALUES ('BT',  'Blood test',  20);

-- Rossi: 3 days, 50 + 300 + 20 = 370      -> F = 20 + 370 - 3*2    = 384
INSERT INTO stays VALUES ('RSSMRA80A01H501U', DATE '2026-03-01', DATE '2026-03-04');
INSERT INTO stay_treatments VALUES ('RSSMRA80A01H501U', DATE '2026-03-01', 'XR');
INSERT INTO stay_treatments VALUES ('RSSMRA80A01H501U', DATE '2026-03-01', 'MRI');
INSERT INTO stay_treatments VALUES ('RSSMRA80A01H501U', DATE '2026-03-01', 'BT');
-- Bianchi: 10 days, 1200 + 20 + 20 = 1240 -> F = 15 + 1240 - 10*1.5 = 1240
INSERT INTO stays VALUES ('BNCLRA75B41F205X', DATE '2026-04-10', DATE '2026-04-20');
INSERT INTO stay_treatments VALUES ('BNCLRA75B41F205X', DATE '2026-04-10', 'SRG');
INSERT INTO stay_treatments VALUES ('BNCLRA75B41F205X', DATE '2026-04-10', 'BT');
INSERT INTO stay_treatments VALUES ('BNCLRA75B41F205X', DATE '2026-04-10', 'BT');
-- Verdi: still in hospital, more expensive, must be ignored
INSERT INTO stays VALUES ('VRDGPP90C10A944K', DATE '2026-04-15', NULL);
INSERT INTO stay_treatments VALUES ('VRDGPP90C10A944K', DATE '2026-04-15', 'SRG');
INSERT INTO stay_treatments VALUES ('VRDGPP90C10A944K', DATE '2026-04-15', 'SRG');
-- Rossi again, outside the period
INSERT INTO stays VALUES ('RSSMRA80A01H501U', DATE '2026-09-01', DATE '2026-09-02');
INSERT INTO stay_treatments VALUES ('RSSMRA80A01H501U', DATE '2026-09-01', 'SRG');
INSERT INTO stay_treatments VALUES ('RSSMRA80A01H501U', DATE '2026-09-01', 'SRG');
COMMIT;
