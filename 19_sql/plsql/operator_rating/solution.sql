SET SERVEROUTPUT ON FORMAT WRAPPED
SET FEEDBACK OFF
SET LINESIZE 120 PAGESIZE 100

CREATE OR REPLACE PROCEDURE operator_rating(p_from DATE) IS
BEGIN
    -- vpos - vneg = sum(minutes * difficulty * result), vpos + vneg = sum(minutes * difficulty)
    FOR r IN (
        SELECT o.id, o.name,
               sum(op.minutes * p.difficulty * c.result) / sum(op.minutes * p.difficulty) AS rating
        FROM calls c
        JOIN operators o   ON o.id = c.operator
        JOIN problems p    ON p.code = c.problem
        JOIN operations op ON op.call_id = c.id
        WHERE c.opened >= p_from AND c.result <> 0
        GROUP BY o.id, o.name
        ORDER BY rating DESC
    ) LOOP
        DBMS_OUTPUT.PUT_LINE(RPAD(r.name, 6) || TO_CHAR(r.rating, 'FM90.000')
                             || CASE WHEN r.rating < 0 THEN '  <- negative' END);
    END LOOP;
END;
/

EXEC operator_rating(DATE '2026-11-01')
