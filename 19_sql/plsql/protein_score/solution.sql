SET SERVEROUTPUT ON FORMAT WRAPPED
SET FEEDBACK OFF
SET LINESIZE 120 PAGESIZE 100

CREATE OR REPLACE PROCEDURE protein_score(p_protein proteins.code%TYPE) IS
    v_sum     NUMBER := 0;
    v_regions NUMBER := 0;
BEGIN
    FOR r IN (
        SELECT r.code,
               avg(CASE WHEN r.potential = a.potential THEN 1
                        WHEN ABS(r.potential - a.potential) = 2 THEN -2
                        ELSE -1 END) AS score
        FROM regions r
        JOIN amino_in_region ar ON ar.region = r.code
        JOIN atom_in_amino aa   ON aa.amino = ar.amino
        JOIN atoms a            ON a.code = aa.atom
        WHERE r.protein = p_protein
        GROUP BY r.code
        ORDER BY r.code
    ) LOOP
        DBMS_OUTPUT.PUT_LINE('region ' || r.code || ': ' || TO_CHAR(r.score, 'FM90.000'));
        v_sum := v_sum + r.score;
        v_regions := v_regions + 1;
    END LOOP;
    IF v_regions = 0 THEN
        DBMS_OUTPUT.PUT_LINE('protein ' || p_protein || ' has no regions');
    ELSE
        DBMS_OUTPUT.PUT_LINE('protein ' || p_protein || ': ' || TO_CHAR(v_sum / v_regions, 'FM90.000'));
    END IF;
END;
/

EXEC protein_score('P1')
