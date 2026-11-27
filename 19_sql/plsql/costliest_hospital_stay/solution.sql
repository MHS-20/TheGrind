SET SERVEROUTPUT ON FORMAT WRAPPED
SET FEEDBACK OFF
SET LINESIZE 120 PAGESIZE 100

CREATE OR REPLACE PROCEDURE costliest_stay(p_from DATE, p_to DATE) IS
    CURSOR c_stays IS
        SELECT s.patient, s.start_date, s.end_date,
               s.end_date - s.start_date AS days,
               CASE WHEN s.end_date - s.start_date < 7
                    THEN 20 + sum(t.cost) - (s.end_date - s.start_date) * 2
                    ELSE 15 + sum(t.cost) - (s.end_date - s.start_date) * 1.5
               END AS f
        FROM stays s
        JOIN stay_treatments st ON st.patient = s.patient AND st.stay_start = s.start_date
        JOIN treatments t       ON t.code = st.treatment
        WHERE s.end_date IS NOT NULL
          AND s.start_date >= p_from AND s.end_date <= p_to
        GROUP BY s.patient, s.start_date, s.end_date
        ORDER BY f DESC;
    v_stay    c_stays%ROWTYPE;
    v_patient patients%ROWTYPE;
BEGIN
    OPEN c_stays;
    FETCH c_stays INTO v_stay;               -- only the first row: the costliest
    CLOSE c_stays;
    IF v_stay.patient IS NULL THEN
        DBMS_OUTPUT.PUT_LINE('No finished stays in the period');
        RETURN;
    END IF;

    SELECT * INTO v_patient FROM patients WHERE tax_code = v_stay.patient;
    DBMS_OUTPUT.PUT_LINE('Patient: ' || v_patient.first_name || ' ' || v_patient.last_name
                         || ' (' || v_patient.tax_code || ')');
    DBMS_OUTPUT.PUT_LINE('Stay: ' || TO_CHAR(v_stay.start_date, 'YYYY-MM-DD') || ' to '
                         || TO_CHAR(v_stay.end_date, 'YYYY-MM-DD') || ', ' || v_stay.days
                         || ' days, F = ' || v_stay.f);
    FOR t IN (SELECT t.name, t.cost
              FROM stay_treatments st JOIN treatments t ON t.code = st.treatment
              WHERE st.patient = v_stay.patient AND st.stay_start = v_stay.start_date
              ORDER BY t.cost DESC) LOOP
        DBMS_OUTPUT.PUT_LINE('  ' || t.name || ': ' || t.cost);
    END LOOP;
END;
/

EXEC costliest_stay(DATE '2026-01-01', DATE '2026-06-30')
