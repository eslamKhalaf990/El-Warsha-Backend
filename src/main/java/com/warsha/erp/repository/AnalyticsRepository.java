package com.warsha.erp.repository;

import com.warsha.erp.entities.Order;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * Raw rows for the analytics dashboard. The queries only collect facts; every
 * figure is worked out in {@link com.warsha.erp.services.AnalyticsCalculator}.
 *
 * Orders.TotalPrice is the amount still due (see OrderPricing), so an order's
 * value is rebuilt from its lines: subtotal − discount + delivery.
 * {@code toExclusive} is the day after the last day of the range.
 */
public interface AnalyticsRepository extends Repository<Order, Long> {

    /**
     * One row per order in the range, cancelled ones included:
     * [orderId, orderDate, status, source, governorate, customerId, customerName, phone,
     *  firstOrderDate, lifetimeOrders, subtotal, cost, units, discount, delivery, due,
     *  paid, paymentMethod]. First order and lifetime count ignore cancelled orders.
     */
    @Query(value = """
        WITH firsts AS (
            SELECT CustomerID, MIN(OrderDate) AS FirstOrder, COUNT(*) AS LifetimeOrders
            FROM dbo.Orders WHERE Status <> 'Cancelled' GROUP BY CustomerID
        ), items AS (
            SELECT oi.OrderID, SUM(oi.Quantity * oi.UnitPrice) AS Subtotal,
                   SUM(oi.Quantity * ISNULL(p.BuyingPrice, 0)) AS Cost, SUM(oi.Quantity) AS Units
            FROM dbo.OrderItems oi JOIN dbo.Products p ON p.ProductID = oi.ProductID
            GROUP BY oi.OrderID
        ), paid AS (
            SELECT OrderID, SUM(AmountPaid) AS Paid, MAX(PaymentMethod) AS Method
            FROM dbo.Payments WHERE PaymentStatus = 'Completed' GROUP BY OrderID
        )
        SELECT o.OrderID, CAST(o.OrderDate AS DATE), o.Status, o.OrderSource, c.Governorate,
               o.CustomerID, c.FullName, c.Phone, CAST(f.FirstOrder AS DATE), ISNULL(f.LifetimeOrders, 0),
               ISNULL(i.Subtotal, 0), ISNULL(i.Cost, 0), ISNULL(i.Units, 0),
               ISNULL(o.Discount, 0), ISNULL(o.DeliveryCharge, 0), ISNULL(o.TotalPrice, 0),
               ISNULL(pd.Paid, 0), pd.Method
        FROM dbo.Orders o
        JOIN dbo.Customers c ON c.CustomerID = o.CustomerID
        LEFT JOIN firsts f ON f.CustomerID = o.CustomerID
        LEFT JOIN items i ON i.OrderID = o.OrderID
        LEFT JOIN paid pd ON pd.OrderID = o.OrderID
        WHERE o.OrderDate >= :from AND o.OrderDate < :toExclusive
        """, nativeQuery = true)
    List<Object[]> orderFacts(@Param("from") LocalDate from, @Param("toExclusive") LocalDate toExclusive);

    /**
     * Sales per product in the range, cancelled orders left out. The order discount is
     * shared over its lines by value:
     * [productId, name, category, units, sales, discountShare, cost].
     */
    @Query(value = """
        WITH subtotals AS (
            SELECT OrderID, SUM(Quantity * UnitPrice) AS Subtotal FROM dbo.OrderItems GROUP BY OrderID
        )
        SELECT p.ProductID, p.Name, c.Name, SUM(oi.Quantity), SUM(oi.Quantity * oi.UnitPrice),
               SUM(CASE WHEN s.Subtotal > 0
                        THEN oi.Quantity * oi.UnitPrice * ISNULL(o.Discount, 0) / s.Subtotal ELSE 0 END),
               SUM(oi.Quantity * ISNULL(p.BuyingPrice, 0))
        FROM dbo.OrderItems oi
        JOIN dbo.Orders o ON o.OrderID = oi.OrderID
        JOIN subtotals s ON s.OrderID = o.OrderID
        JOIN dbo.Products p ON p.ProductID = oi.ProductID
        LEFT JOIN dbo.Categories c ON c.CategoryID = p.Category
        WHERE o.Status <> 'Cancelled' AND o.OrderDate >= :from AND o.OrderDate < :toExclusive
        GROUP BY p.ProductID, p.Name, c.Name
        """, nativeQuery = true)
    List<Object[]> productSales(@Param("from") LocalDate from, @Param("toExclusive") LocalDate toExclusive);

    /**
     * Every product that is not deleted, with its stock and when it last sold:
     * [productId, name, category, quantity, buyingPrice, lastSold].
     */
    @Query(value = """
        SELECT p.ProductID, p.Name, c.Name, p.Quantity, ISNULL(p.BuyingPrice, 0),
               (SELECT CAST(MAX(o.OrderDate) AS DATE) FROM dbo.OrderItems oi
                JOIN dbo.Orders o ON o.OrderID = oi.OrderID
                WHERE oi.ProductID = p.ProductID AND o.Status <> 'Cancelled')
        FROM dbo.Products p
        LEFT JOIN dbo.Categories c ON c.CategoryID = p.Category
        WHERE p.IsDeleted IS NULL OR p.IsDeleted <> 'true'
        """, nativeQuery = true)
    List<Object[]> productStock();

    /** The day of the first order (a SQL date), or null when there are none. */
    @Query(value = "SELECT CAST(MIN(OrderDate) AS DATE) FROM dbo.Orders", nativeQuery = true)
    Object firstOrderDate();
}
