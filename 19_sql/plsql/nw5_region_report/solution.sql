-- Needs the Northwind tables: run ../northwind/northwind.sql first.
SET SERVEROUTPUT ON FORMAT WRAPPED
SET FEEDBACK OFF

CREATE OR REPLACE PROCEDURE region_report(p_region customers.Region%TYPE) IS
    v_shipper shippers.CompanyName%TYPE;
    v_orders  NUMBER;
BEGIN
    SELECT CompanyName, n INTO v_shipper, v_orders FROM (
        SELECT s.CompanyName, count(*) AS n
        FROM orders o
        JOIN customers c ON c.CustomerID = o.CustomerID
        JOIN shippers s  ON s.ShipperID = o.ShipVia
        WHERE c.Region = p_region
        GROUP BY s.ShipperID, s.CompanyName
        ORDER BY n DESC, s.CompanyName
    ) WHERE ROWNUM = 1;
    DBMS_OUTPUT.PUT_LINE('Most used shipper in ' || p_region || ': ' || v_shipper || ' (' || v_orders || ' orders)');

    DBMS_OUTPUT.PUT_LINE('Top products in ' || p_region || ':');
    FOR p IN (
        SELECT pr.ProductName, sum(d.Quantity) AS q
        FROM orders o
        JOIN customers c      ON c.CustomerID = o.CustomerID
        JOIN order_details d  ON d.OrderID = o.OrderID
        JOIN products pr      ON pr.ProductID = d.ProductID
        WHERE c.Region = p_region
        GROUP BY pr.ProductID, pr.ProductName
        ORDER BY q DESC, pr.ProductName
        FETCH FIRST 10 ROWS ONLY
    ) LOOP
        DBMS_OUTPUT.PUT_LINE('  ' || p.ProductName || ' (' || p.q || ')');
    END LOOP;
EXCEPTION
    WHEN NO_DATA_FOUND THEN
        DBMS_OUTPUT.PUT_LINE('No orders from region ' || p_region);
END;
/

EXEC region_report('AK')
