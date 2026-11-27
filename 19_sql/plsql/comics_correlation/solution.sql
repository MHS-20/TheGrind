SET SERVEROUTPUT ON FORMAT WRAPPED
SET FEEDBACK OFF
SET LINESIZE 120 PAGESIZE 100

CREATE OR REPLACE FUNCTION comics_correlation(p_comic1 comics.name%TYPE, p_comic2 comics.name%TYPE)
RETURN NUMBER IS
    v_total NUMBER;
BEGIN
    SELECT count(*) INTO v_total
    FROM appears_in a2
    WHERE a2.comic = p_comic2
      AND a2.character IN (SELECT a1.character FROM appears_in a1 WHERE a1.comic = p_comic1);
    RETURN v_total;
END;
/

SELECT comics_correlation('Mouse Tales', 'Duck City') AS mouse_in_duck,
       comics_correlation('Duck City', 'Mouse Tales') AS duck_in_mouse
FROM dual;
