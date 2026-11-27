-- Needs the Northwind tables: run ../northwind/northwind.sql first.
SET SERVEROUTPUT ON FORMAT WRAPPED
SET FEEDBACK OFF

CREATE OR REPLACE FUNCTION category_correlation(p_cat1 NUMBER, p_cat2 NUMBER) RETURN NUMBER IS
    v_pairs NUMBER;
BEGIN
    -- one row per (line of category 1, line of category 2) in the same order;
    -- within one category, each unordered pair of products is counted once
    SELECT count(*) INTO v_pairs
    FROM order_details d1
    JOIN products p1      ON p1.ProductID = d1.ProductID
    JOIN order_details d2 ON d2.OrderID = d1.OrderID
    JOIN products p2      ON p2.ProductID = d2.ProductID
    WHERE p1.CategoryID = p_cat1
      AND p2.CategoryID = p_cat2
      AND (p_cat1 <> p_cat2 OR d1.ProductID < d2.ProductID);
    RETURN v_pairs;
END;
/

SELECT category_correlation(1, 2) AS c12, category_correlation(2, 1) AS c21,
       category_correlation(1, 8) AS c18, category_correlation(1, 1) AS c11
FROM dual;
