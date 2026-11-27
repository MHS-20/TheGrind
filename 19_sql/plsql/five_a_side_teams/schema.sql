CREATE TABLE players (
    id        NUMBER PRIMARY KEY,
    name      VARCHAR2(40) NOT NULL,
    technical NUMBER NOT NULL,
    athletic  NUMBER NOT NULL,
    role      VARCHAR2(10) NOT NULL CHECK (role IN ('ATTACKER', 'DEFENDER', 'GOALKEEPER'))
);
CREATE TABLE matches (id NUMBER PRIMARY KEY, played_on DATE NOT NULL);
CREATE TABLE availability (
    match_id  NUMBER REFERENCES matches,
    player_id NUMBER REFERENCES players,
    PRIMARY KEY (match_id, player_id)
);
CREATE TABLE lineups (
    match_id  NUMBER REFERENCES matches,
    player_id NUMBER REFERENCES players,
    team      NUMBER NOT NULL CHECK (team IN (1, 2)),
    PRIMARY KEY (match_id, player_id)
);

-- score 17.8
INSERT INTO players VALUES (1,  'Ada', 9, 7, 'ATTACKER');
-- 16.4
INSERT INTO players VALUES (2,  'Bob', 7, 8, 'ATTACKER');
-- 13.2
INSERT INTO players VALUES (3,  'Cat', 6, 6, 'ATTACKER');
-- 15.0
INSERT INTO players VALUES (4,  'Dan', 5, 9, 'ATTACKER');
-- 17.6
INSERT INTO players VALUES (5,  'Eve', 8, 8, 'DEFENDER');
-- 16.2
INSERT INTO players VALUES (6,  'Fay', 6, 9, 'DEFENDER');
-- 9.8
INSERT INTO players VALUES (7,  'Gus', 4, 5, 'DEFENDER');
-- 12.4
INSERT INTO players VALUES (8,  'Hal', 7, 4, 'DEFENDER');
-- 15.6
INSERT INTO players VALUES (9,  'Ivy', 8, 6, 'GOALKEEPER');
-- 14.2
INSERT INTO players VALUES (10, 'Jon', 6, 7, 'GOALKEEPER');
-- not available
INSERT INTO players VALUES (11, 'Kim', 3, 3, 'ATTACKER');
INSERT INTO matches VALUES (1, DATE '2026-12-05');
INSERT INTO matches VALUES (2, DATE '2026-12-12');
INSERT INTO availability SELECT 1, id FROM players WHERE id <= 10;
-- only 8
INSERT INTO availability SELECT 2, id FROM players WHERE id <= 8;
COMMIT;
