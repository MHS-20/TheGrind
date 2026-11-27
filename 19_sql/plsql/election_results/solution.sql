SET SERVEROUTPUT ON FORMAT WRAPPED
SET FEEDBACK OFF
SET LINESIZE 120 PAGESIZE 100

CREATE OR REPLACE PROCEDURE elected(p_district districts.id%TYPE) IS
    CURSOR c_parties IS
        SELECT p.id, p.name, v.votes
        FROM votes v JOIN parties p ON p.id = v.party
        WHERE v.district = p_district
        ORDER BY v.votes DESC;
    CURSOR c_candidates(p_party NUMBER) IS
        SELECT first_name, last_name, preferences
        FROM candidates
        WHERE party = p_party AND district = p_district
        ORDER BY preferences DESC;
    v_seats       NUMBER;
    v_total_votes NUMBER;
    v_party_seats NUMBER;
BEGIN
    SELECT seats INTO v_seats FROM districts WHERE id = p_district;
    SELECT sum(votes) INTO v_total_votes FROM votes WHERE district = p_district;
    DBMS_OUTPUT.PUT_LINE('District ' || p_district || ': ' || v_seats || ' seats, ' || v_total_votes || ' votes');

    FOR p IN c_parties LOOP
        v_party_seats := ROUND(p.votes / v_total_votes * v_seats);
        DBMS_OUTPUT.PUT_LINE(p.name || ': ' || v_party_seats || ' seats');
        FOR c IN c_candidates(p.id) LOOP
            EXIT WHEN c_candidates%ROWCOUNT > v_party_seats;
            DBMS_OUTPUT.PUT_LINE('  ' || c.first_name || ' ' || c.last_name || ' (' || c.preferences || ')');
        END LOOP;
    END LOOP;
EXCEPTION
    WHEN NO_DATA_FOUND THEN
        DBMS_OUTPUT.PUT_LINE('No district ' || p_district);
END;
/

EXEC elected(1)
