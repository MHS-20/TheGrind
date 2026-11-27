-- Needs the Northwind tables: run ../northwind/northwind.sql first.
SET SERVEROUTPUT ON FORMAT WRAPPED
SET FEEDBACK OFF

CREATE OR REPLACE PROCEDURE customer_tiers(p_min NUMBER, p_max NUMBER) IS
BEGIN
    FOR c IN (SELECT c.CompanyName, count(o.OrderID) AS n
              FROM customers c
              LEFT JOIN orders o ON o.CustomerID = c.CustomerID
              GROUP BY c.CustomerID, c.CompanyName
              ORDER BY n DESC, c.CompanyName) LOOP
        DBMS_OUTPUT.PUT_LINE(c.CompanyName || ': ' || c.n || ' orders, ' ||
            CASE WHEN c.n > p_max  THEN 'high'
                 WHEN c.n >= p_min THEN 'medium'
                 ELSE 'low' END);
    END LOOP;
END;
/

EXEC customer_tiers(5, 15)
