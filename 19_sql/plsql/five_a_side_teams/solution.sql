SET SERVEROUTPUT ON FORMAT WRAPPED
SET FEEDBACK OFF
SET LINESIZE 120 PAGESIZE 100

CREATE OR REPLACE PROCEDURE generate_teams(p_match matches.id%TYPE) IS
    CURSOR c_players IS
        SELECT p.id, p.role
        FROM availability a JOIN players p ON p.id = a.player_id
        WHERE a.match_id = p_match
        ORDER BY p.role, p.technical * 1.2 + p.athletic DESC, p.id;
    v_available  NUMBER;
    v_role       players.role%TYPE;
    v_first_team NUMBER := 2;       -- flipped to 1 for the first role
    v_team       NUMBER;
    e_not_enough EXCEPTION;
BEGIN
    SELECT count(*) INTO v_available FROM availability WHERE match_id = p_match;
    IF v_available < 10 THEN
        RAISE e_not_enough;
    END IF;

    DELETE FROM lineups WHERE match_id = p_match;
    FOR p IN c_players LOOP
        IF v_role IS NULL OR p.role <> v_role THEN     -- a new role starts
            v_role := p.role;
            v_first_team := 3 - v_first_team;          -- the other team picks first
            v_team := v_first_team;
        END IF;
        INSERT INTO lineups VALUES (p_match, p.id, v_team);
        v_team := 3 - v_team;                          -- alternate
    END LOOP;
EXCEPTION
    WHEN e_not_enough THEN
        RAISE_APPLICATION_ERROR(-20001, 'only ' || v_available || ' players available for match ' || p_match
                                        || ', at least 10 needed');
END;
/

EXEC generate_teams(1)
COLUMN role FORMAT A10
COLUMN name FORMAT A5
SELECT l.team, p.role, p.name, p.technical * 1.2 + p.athletic AS score
FROM lineups l JOIN players p ON p.id = l.player_id
WHERE l.match_id = 1
ORDER BY l.team, p.role, score DESC;

EXEC generate_teams(2)
