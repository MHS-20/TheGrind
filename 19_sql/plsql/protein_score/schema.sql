CREATE TABLE proteins (code VARCHAR2(10) PRIMARY KEY, name VARCHAR2(40), function VARCHAR2(40), class VARCHAR2(20));
CREATE TABLE amino_acids (code VARCHAR2(10) PRIMARY KEY, name VARCHAR2(40), function VARCHAR2(40));
CREATE TABLE atoms (code VARCHAR2(10) PRIMARY KEY, name VARCHAR2(20), potential NUMBER CHECK (potential IN (-1, 0, 1)));
CREATE TABLE regions (
    code      VARCHAR2(10) PRIMARY KEY,
    protein   VARCHAR2(10) REFERENCES proteins,
    position  NUMBER,
    potential NUMBER CHECK (potential IN (-1, 0, 1))
);
CREATE TABLE atom_in_amino (
    amino    VARCHAR2(10) REFERENCES amino_acids,
    atom     VARCHAR2(10) REFERENCES atoms,
    position NUMBER,
    PRIMARY KEY (amino, position)
);
CREATE TABLE amino_in_region (
    region   VARCHAR2(10) REFERENCES regions,
    amino    VARCHAR2(10) REFERENCES amino_acids,
    position NUMBER,
    PRIMARY KEY (region, position)
);

INSERT INTO proteins VALUES ('P1', 'Demo protein', NULL, NULL);
INSERT INTO atoms VALUES ('N', 'neg',     -1);
INSERT INTO atoms VALUES ('Z', 'neutral',  0);
INSERT INTO atoms VALUES ('P', 'pos',      1);
-- amino acid A: atoms -, -, 0   amino acid B: atoms +, 0
INSERT INTO amino_acids VALUES ('A', 'Alpha', NULL);
INSERT INTO amino_acids VALUES ('B', 'Beta',  NULL);
INSERT INTO atom_in_amino VALUES ('A', 'N', 1);
INSERT INTO atom_in_amino VALUES ('A', 'N', 2);
INSERT INTO atom_in_amino VALUES ('A', 'Z', 3);
INSERT INTO atom_in_amino VALUES ('B', 'P', 1);
INSERT INTO atom_in_amino VALUES ('B', 'Z', 2);
-- R1 (potential -): A, A          atoms - - 0 - - 0        weights +1 +1 -1 +1 +1 -1  -> 2/6
-- R2 (potential +): B, A          atoms + 0 - - 0          weights +1 -1 -2 -2 -1     -> -5/5
-- R3 (potential 0): B             atoms + 0                weights -1 +1              -> 0/2
INSERT INTO regions VALUES ('R1', 'P1', 1, -1);
INSERT INTO regions VALUES ('R2', 'P1', 2,  1);
INSERT INTO regions VALUES ('R3', 'P1', 3,  0);
INSERT INTO amino_in_region VALUES ('R1', 'A', 1);
INSERT INTO amino_in_region VALUES ('R1', 'A', 2);
INSERT INTO amino_in_region VALUES ('R2', 'B', 1);
INSERT INTO amino_in_region VALUES ('R2', 'A', 2);
INSERT INTO amino_in_region VALUES ('R3', 'B', 1);
COMMIT;
