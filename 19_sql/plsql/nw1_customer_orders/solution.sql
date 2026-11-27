-- Needs the Northwind tables: run ../northwind/northwind.sql first.
SET SERVEROUTPUT ON FORMAT WRAPPED
SET FEEDBACK OFF

CREATE OR REPLACE PROCEDURE customer_orders(p_customer customers.CustomerID%TYPE) IS
    CURSOR c_orders IS
        SELECT OrderID, OrderDate FROM orders
        WHERE CustomerID = p_customer
        ORDER BY OrderDate, OrderID;
    CURSOR c_lines(p_order NUMBER) IS
        SELECT ProductID, Quantity FROM order_details
        WHERE OrderID = p_order
        ORDER BY ProductID;
BEGIN
    FOR o IN c_orders LOOP
        DBMS_OUTPUT.PUT_LINE('Order ' || o.OrderID || ' on ' || TO_CHAR(o.OrderDate, 'YYYY-MM-DD') || ':');
        FOR l IN c_lines(o.OrderID) LOOP
            DBMS_OUTPUT.PUT_LINE('- product ' || l.ProductID || ' quantity ' || l.Quantity);
        END LOOP;
    END LOOP;
END;
/

EXEC customer_orders('ANATR')
