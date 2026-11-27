-- Needs the Northwind tables: run ../northwind/northwind.sql first.
SET SERVEROUTPUT ON FORMAT WRAPPED
SET FEEDBACK OFF

CREATE OR REPLACE PROCEDURE restock_report(p_supplier suppliers.SupplierID%TYPE) IS
    v_name suppliers.CompanyName%TYPE;
BEGIN
    SELECT CompanyName INTO v_name FROM suppliers WHERE SupplierID = p_supplier;
    DBMS_OUTPUT.PUT_LINE('Products to reorder from ' || v_name || ':');
    FOR p IN (
        SELECT pr.ProductName, pr.UnitsInStock, pr.ReorderLevel, count(*) AS pending
        FROM products pr
        JOIN order_details d ON d.ProductID = pr.ProductID
        JOIN orders o        ON o.OrderID = d.OrderID
        WHERE pr.SupplierID = p_supplier
          AND pr.UnitsInStock < pr.ReorderLevel
          AND o.ShippedDate IS NULL
        GROUP BY pr.ProductID, pr.ProductName, pr.UnitsInStock, pr.ReorderLevel
        ORDER BY pr.ProductID
    ) LOOP
        DBMS_OUTPUT.PUT_LINE('  ' || p.ProductName || ' (' || p.UnitsInStock || ' in stock vs reorder level '
                             || p.ReorderLevel || ', ' || p.pending || ' unshipped orders)');
    END LOOP;
EXCEPTION
    WHEN NO_DATA_FOUND THEN
        DBMS_OUTPUT.PUT_LINE('No supplier ' || p_supplier);
END;
/

EXEC restock_report(1)
