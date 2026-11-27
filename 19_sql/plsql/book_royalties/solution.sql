SET SERVEROUTPUT ON FORMAT WRAPPED
SET FEEDBACK OFF
SET LINESIZE 120 PAGESIZE 100

CREATE OR REPLACE FUNCTION royalties(p_book books.id%TYPE, p_year NUMBER, p_previous NUMBER)
RETURN NUMBER IS
    -- only the brackets that the range (p_lo, p_hi] reaches
    CURSOR c_brackets(p_lo NUMBER, p_hi NUMBER) IS
        SELECT cum_from, cum_to, percent
        FROM brackets
        WHERE book = p_book AND cum_to > p_lo AND cum_from <= p_hi
        ORDER BY cum_from;
    v_year   NUMBER;
    v_total  NUMBER := 0;
    v_lower  NUMBER;
    v_amount NUMBER;
BEGIN
    SELECT NVL(sum(amount), 0) INTO v_year
    FROM sales
    WHERE book = p_book AND EXTRACT(YEAR FROM sold_on) = p_year;

    FOR b IN c_brackets(p_previous, p_previous + v_year) LOOP
        v_lower  := GREATEST(b.cum_from - 1, 0);        -- brackets are written 0-30000, 30001-175000
        v_amount := LEAST(b.cum_to, p_previous + v_year) - GREATEST(v_lower, p_previous);
        DBMS_OUTPUT.PUT_LINE('  ' || v_amount || ' at ' || b.percent || '%');
        v_total := v_total + v_amount * b.percent / 100;
    END LOOP;
    RETURN v_total;
END;
/

BEGIN
    DBMS_OUTPUT.PUT_LINE('2012, after 130,000: ' || royalties('AS45', 2012, 130000));
    DBMS_OUTPUT.PUT_LINE('2012, first year:    ' || royalties('AS45', 2012, 0));
END;
/
