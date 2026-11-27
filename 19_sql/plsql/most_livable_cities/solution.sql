SET SERVEROUTPUT ON FORMAT WRAPPED
SET FEEDBACK OFF
SET LINESIZE 120 PAGESIZE 100

CREATE OR REPLACE PROCEDURE most_livable(p_region regions.name%TYPE) IS
BEGIN
    DELETE FROM livability WHERE region = p_region;

    INSERT INTO livability (city, region, value)
    WITH cleanest AS (                      -- step 1: the 10 least polluted cities
        SELECT city
        FROM parameters
        WHERE region = p_region AND parameter_type = 'Pollution'
        GROUP BY city
        ORDER BY avg(value)
        FETCH FIRST 10 ROWS ONLY
    ), scored AS (                          -- step 2: the formula, for those 10 only
        SELECT c.name, c.region,
               max(CASE WHEN p.parameter = 'GreenArea' THEN p.value END) / c.population
               * (1 + NVL(max(CASE WHEN p.parameter = 'CulturalCentres' THEN p.value END), 0) / 10) AS value
        FROM cities c
        JOIN parameters p ON p.city = c.name AND p.region = c.region
        WHERE c.region = p_region AND c.name IN (SELECT city FROM cleanest)
        GROUP BY c.name, c.region, c.population
    )
    SELECT name, region, value
    FROM scored
    ORDER BY value DESC
    FETCH FIRST 5 ROWS ONLY;
END;
/

EXEC most_livable('Emilia')
COLUMN city FORMAT A8
SELECT city, ROUND(value, 3) AS value FROM livability ORDER BY value DESC;
