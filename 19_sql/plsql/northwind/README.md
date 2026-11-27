# Northwind for Oracle

The Northwind sample database (customers, orders, products, employees, suppliers, shippers), converted for Oracle from Microsoft's `instnwnd.sql` in [sql-server-samples](https://github.com/microsoft/sql-server-samples) (MIT licence). Pictures, photos, notes and some address columns are left out.

```
sqlplus user/password@service @northwind.sql
```

It creates 8 tables and loads 830 orders with 2,155 order lines. The self-reference `employees.ReportsTo` is `DEFERRABLE INITIALLY DEFERRED`: employee 1 reports to employee 2, who is inserted later, so the constraint is checked at `COMMIT` instead of on each insert.

```
categories(CategoryID, CategoryName, Description)
suppliers(SupplierID, CompanyName, ContactName, City, Region, Country)
products(ProductID, ProductName, SupplierID, CategoryID, QuantityPerUnit, UnitPrice,
         UnitsInStock, UnitsOnOrder, ReorderLevel, Discontinued)
shippers(ShipperID, CompanyName, Phone)
customers(CustomerID, CompanyName, ContactName, City, Region, PostalCode, Country, Phone)
employees(EmployeeID, LastName, FirstName, Title, BirthDate, HireDate, City, Region, Country, ReportsTo)
orders(OrderID, CustomerID, EmployeeID, OrderDate, RequiredDate, ShippedDate, ShipVia,
       Freight, ShipName, ShipCity, ShipRegion, ShipCountry)
order_details(OrderID, ProductID, UnitPrice, Quantity, Discount)
```
