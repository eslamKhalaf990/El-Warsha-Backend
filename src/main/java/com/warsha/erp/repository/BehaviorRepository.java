package com.warsha.erp.repository;

import com.warsha.erp.entities.StoreEvent;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Raw rows for the behavior reports; every figure is worked out in
 * {@link com.warsha.erp.services.BehaviorReportService}. Ranges are
 * {@code from <= OccurredAt < toExclusive}.
 */
public interface BehaviorRepository extends Repository<StoreEvent, Long> {

    /**
     * One row: [visitors, sessions, pageViews, productViews, cartAdds, cartRemoves,
     * checkoutsStarted, orders, orderValue].
     */
    @Query(value = """
        SELECT COUNT(DISTINCT VisitorID), COUNT(DISTINCT SessionID),
               SUM(CASE WHEN EventType = 'PAGE_VIEW' THEN 1 ELSE 0 END),
               SUM(CASE WHEN EventType = 'PRODUCT_VIEW' THEN 1 ELSE 0 END),
               SUM(CASE WHEN EventType = 'CART_ADD' THEN 1 ELSE 0 END),
               SUM(CASE WHEN EventType = 'CART_REMOVE' THEN 1 ELSE 0 END),
               SUM(CASE WHEN EventType = 'CHECKOUT_START' THEN 1 ELSE 0 END),
               SUM(CASE WHEN EventType = 'ORDER_PLACED' THEN 1 ELSE 0 END),
               SUM(CASE WHEN EventType = 'ORDER_PLACED' THEN ISNULL(OrderValue, 0) ELSE 0 END)
        FROM dbo.StoreEvents
        WHERE OccurredAt >= :from AND OccurredAt < :toExclusive
        """, nativeQuery = true)
    List<Object[]> totals(@Param("from") LocalDateTime from, @Param("toExclusive") LocalDateTime toExclusive);

    @Query(value = "SELECT COUNT(*) FROM dbo.StoreVisitors WHERE FirstSeenAt >= :from AND FirstSeenAt < :toExclusive",
            nativeQuery = true)
    long newVisitors(@Param("from") LocalDateTime from, @Param("toExclusive") LocalDateTime toExclusive);

    /** One row: [sessions, viewedProduct, addedToCart, startedCheckout, ordered]. */
    @Query(value = """
        WITH s AS (
            SELECT SessionID,
                   MAX(CASE WHEN EventType = 'PRODUCT_VIEW' THEN 1 ELSE 0 END) AS Viewed,
                   MAX(CASE WHEN EventType = 'CART_ADD' THEN 1 ELSE 0 END) AS Added,
                   MAX(CASE WHEN EventType = 'CHECKOUT_START' THEN 1 ELSE 0 END) AS Checkout,
                   MAX(CASE WHEN EventType = 'ORDER_PLACED' THEN 1 ELSE 0 END) AS Ordered
            FROM dbo.StoreEvents
            WHERE OccurredAt >= :from AND OccurredAt < :toExclusive
            GROUP BY SessionID
        )
        SELECT COUNT(*), ISNULL(SUM(Viewed), 0), ISNULL(SUM(Added), 0), ISNULL(SUM(Checkout), 0), ISNULL(SUM(Ordered), 0)
        FROM s
        """, nativeQuery = true)
    List<Object[]> funnel(@Param("from") LocalDateTime from, @Param("toExclusive") LocalDateTime toExclusive);

    /** [deviceType or null, sessions]. */
    @Query(value = """
        SELECT DeviceType, COUNT(DISTINCT SessionID)
        FROM dbo.StoreEvents
        WHERE OccurredAt >= :from AND OccurredAt < :toExclusive
        GROUP BY DeviceType
        """, nativeQuery = true)
    List<Object[]> devices(@Param("from") LocalDateTime from, @Param("toExclusive") LocalDateTime toExclusive);

    /** [date, visitors, sessions, productViews, cartAdds, orders], oldest first. */
    @Query(value = """
        SELECT CAST(OccurredAt AS DATE), COUNT(DISTINCT VisitorID), COUNT(DISTINCT SessionID),
               SUM(CASE WHEN EventType = 'PRODUCT_VIEW' THEN 1 ELSE 0 END),
               SUM(CASE WHEN EventType = 'CART_ADD' THEN 1 ELSE 0 END),
               SUM(CASE WHEN EventType = 'ORDER_PLACED' THEN 1 ELSE 0 END)
        FROM dbo.StoreEvents
        WHERE OccurredAt >= :from AND OccurredAt < :toExclusive
        GROUP BY CAST(OccurredAt AS DATE)
        ORDER BY CAST(OccurredAt AS DATE)
        """, nativeQuery = true)
    List<Object[]> days(@Param("from") LocalDateTime from, @Param("toExclusive") LocalDateTime toExclusive);

    /** [productId, name, views, viewers, cartAdds, cartRemoves]; name is null for deleted products. */
    @Query(value = """
        SELECT e.ProductID, p.Name,
               SUM(CASE WHEN e.EventType = 'PRODUCT_VIEW' THEN 1 ELSE 0 END),
               COUNT(DISTINCT CASE WHEN e.EventType = 'PRODUCT_VIEW' THEN e.VisitorID END),
               SUM(CASE WHEN e.EventType = 'CART_ADD' THEN 1 ELSE 0 END),
               SUM(CASE WHEN e.EventType = 'CART_REMOVE' THEN 1 ELSE 0 END)
        FROM dbo.StoreEvents e
        LEFT JOIN dbo.Products p ON p.ProductID = e.ProductID
        WHERE e.ProductID IS NOT NULL AND e.OccurredAt >= :from AND e.OccurredAt < :toExclusive
        GROUP BY e.ProductID, p.Name
        """, nativeQuery = true)
    List<Object[]> productInterest(@Param("from") LocalDateTime from, @Param("toExclusive") LocalDateTime toExclusive);

    /** Lines of tracked store orders: [productId, name, orders, units]. */
    @Query(value = """
        SELECT oi.ProductID, p.Name, COUNT(DISTINCT oi.OrderID), SUM(oi.Quantity)
        FROM dbo.StoreEvents e
        JOIN dbo.OrderItems oi ON oi.OrderID = e.OrderID
        LEFT JOIN dbo.Products p ON p.ProductID = oi.ProductID
        WHERE e.EventType = 'ORDER_PLACED' AND e.OccurredAt >= :from AND e.OccurredAt < :toExclusive
        GROUP BY oi.ProductID, p.Name
        """, nativeQuery = true)
    List<Object[]> productOrders(@Param("from") LocalDateTime from, @Param("toExclusive") LocalDateTime toExclusive);

    /**
     * Abandoned carts, most recent first; the customer is the cart's, else the visitor's
     * once they logged in: [visitorId, customerId, fullName, phone, email,
     * items JSON, itemCount, cartValue, updatedAt, firstUtmSource, firstReferrer, deviceType].
     */
    @Query(value = """
        SELECT TOP 500 sc.VisitorID, COALESCE(sc.CustomerID, v.CustomerID), c.FullName, c.Phone, c.Email,
               sc.Items, sc.ItemCount, sc.CartValue, sc.UpdatedAt,
               v.FirstUtmSource, v.FirstReferrer, v.DeviceType
        FROM dbo.StoreCarts sc
        LEFT JOIN dbo.StoreVisitors v ON v.VisitorID = sc.VisitorID
        LEFT JOIN dbo.Customers c ON c.CustomerID = COALESCE(sc.CustomerID, v.CustomerID)
        WHERE sc.Status = 'ACTIVE' AND sc.UpdatedAt < :idleBefore
          AND sc.UpdatedAt >= :from AND sc.UpdatedAt < :toExclusive
        ORDER BY sc.UpdatedAt DESC
        """, nativeQuery = true)
    List<Object[]> abandonedCarts(@Param("from") LocalDateTime from, @Param("toExclusive") LocalDateTime toExclusive,
                                  @Param("idleBefore") LocalDateTime idleBefore);

    /** One row: [count, value] of all abandoned carts in the range (not capped like the list). */
    @Query(value = """
        SELECT COUNT(*), ISNULL(SUM(CartValue), 0)
        FROM dbo.StoreCarts
        WHERE Status = 'ACTIVE' AND UpdatedAt < :idleBefore AND UpdatedAt >= :from AND UpdatedAt < :toExclusive
        """, nativeQuery = true)
    List<Object[]> abandonedTotals(@Param("from") LocalDateTime from, @Param("toExclusive") LocalDateTime toExclusive,
                                   @Param("idleBefore") LocalDateTime idleBefore);

    @Query(value = "SELECT COUNT(*) FROM dbo.StoreCarts WHERE Status = 'ORDERED' AND OrderedAt >= :from AND OrderedAt < :toExclusive",
            nativeQuery = true)
    long orderedCarts(@Param("from") LocalDateTime from, @Param("toExclusive") LocalDateTime toExclusive);

    /** [query (lower case), searches, visitors, averageResults or null, noResultSearches]. */
    @Query(value = """
        SELECT LOWER(SearchQuery), COUNT(*), COUNT(DISTINCT VisitorID),
               AVG(CAST(ResultCount AS FLOAT)),
               SUM(CASE WHEN ResultCount = 0 THEN 1 ELSE 0 END)
        FROM dbo.StoreEvents
        WHERE EventType = 'SEARCH' AND SearchQuery IS NOT NULL
          AND OccurredAt >= :from AND OccurredAt < :toExclusive
        GROUP BY LOWER(SearchQuery)
        """, nativeQuery = true)
    List<Object[]> searches(@Param("from") LocalDateTime from, @Param("toExclusive") LocalDateTime toExclusive);

    /** [code, result, tries, visitors]. */
    @Query(value = """
        SELECT VoucherCode, VoucherResult, COUNT(*), COUNT(DISTINCT VisitorID)
        FROM dbo.StoreEvents
        WHERE EventType = 'VOUCHER_APPLY' AND VoucherCode IS NOT NULL
          AND OccurredAt >= :from AND OccurredAt < :toExclusive
        GROUP BY VoucherCode, VoucherResult
        """, nativeQuery = true)
    List<Object[]> vouchers(@Param("from") LocalDateTime from, @Param("toExclusive") LocalDateTime toExclusive);

    /**
     * Visitors active in the range, grouped by where they first came from:
     * [firstUtmSource, firstReferrer, visitors, orderingVisitors, orders, orderValue].
     */
    @Query(value = """
        WITH active AS (
            SELECT DISTINCT VisitorID FROM dbo.StoreEvents
            WHERE OccurredAt >= :from AND OccurredAt < :toExclusive
        ), ordered AS (
            SELECT VisitorID, COUNT(*) AS Orders, SUM(ISNULL(OrderValue, 0)) AS Value
            FROM dbo.StoreEvents
            WHERE EventType = 'ORDER_PLACED' AND OccurredAt >= :from AND OccurredAt < :toExclusive
            GROUP BY VisitorID
        )
        SELECT v.FirstUtmSource, v.FirstReferrer, COUNT(*),
               SUM(CASE WHEN o.VisitorID IS NULL THEN 0 ELSE 1 END),
               ISNULL(SUM(o.Orders), 0), ISNULL(SUM(o.Value), 0)
        FROM active a
        JOIN dbo.StoreVisitors v ON v.VisitorID = a.VisitorID
        LEFT JOIN ordered o ON o.VisitorID = a.VisitorID
        GROUP BY v.FirstUtmSource, v.FirstReferrer
        """, nativeQuery = true)
    List<Object[]> sources(@Param("from") LocalDateTime from, @Param("toExclusive") LocalDateTime toExclusive);
}
