-- Needs the Northwind tables: run ../northwind/northwind.sql first.
SET SERVEROUTPUT ON FORMAT WRAPPED
SET FEEDBACK OFF

CREATE OR REPLACE PROCEDURE employee_productivity(p_from DATE, p_to DATE) IS
BEGIN
    FOR e IN (
        WITH p AS (
            SELECT * FROM orders WHERE OrderDate BETWEEN p_from AND p_to
        ), tot AS (
            SELECT count(DISTINCT CustomerID) AS customers,
                   count(DISTINCT OrderDate)  AS work_days,
                   count(*)                   AS orders
            FROM p
        )
        SELECT e.LastName, e.FirstName,
               count(DISTINCT p.CustomerID) / tot.customers AS f1,
               count(DISTINCT p.OrderDate)  / tot.work_days AS f2,
               count(*)                     / tot.orders    AS f3
        FROM p
        JOIN employees e ON e.EmployeeID = p.EmployeeID
        CROSS JOIN tot
        GROUP BY e.EmployeeID, e.LastName, e.FirstName, tot.customers, tot.work_days, tot.orders
        ORDER BY f1 + f2 + f3 DESC
    ) LOOP
        DBMS_OUTPUT.PUT_LINE(e.LastName || ' ' || e.FirstName
            || ' F=' || TO_CHAR(e.f1 + e.f2 + e.f3, 'FM0.000')
            || ' (' || TO_CHAR(e.f1, 'FM0.000') || ', ' || TO_CHAR(e.f2, 'FM0.000')
            || ', ' || TO_CHAR(e.f3, 'FM0.000') || ')');
    END LOOP;
END;
/

EXEC employee_productivity(DATE '1996-01-29', DATE '1997-05-29')
