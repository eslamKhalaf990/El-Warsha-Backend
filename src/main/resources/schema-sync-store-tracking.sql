-- =====================================================================
-- Warsha Backend: store visitor behavior tracking.
-- StoreEvents is an append-only log written by POST /events; it has no
-- foreign keys so deleting a product or customer never touches history.
-- Safe to run more than once.
-- =====================================================================

-- StoreVisitors (StoreVisitor entity): one row per browser (random visitor id).
IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'StoreVisitors')
BEGIN
    CREATE TABLE StoreVisitors (
        VisitorID VARCHAR(36) NOT NULL PRIMARY KEY,
        CustomerID INT NULL,                         -- set once the visitor logs in
        FirstSeenAt DATETIME2 NOT NULL,
        LastSeenAt DATETIME2 NOT NULL,
        FirstLandingPage NVARCHAR(200) NULL,
        FirstReferrer NVARCHAR(300) NULL,
        FirstUtmSource NVARCHAR(100) NULL,
        FirstUtmMedium NVARCHAR(100) NULL,
        FirstUtmCampaign NVARCHAR(100) NULL,
        DeviceType VARCHAR(10) NULL                  -- MOBILE, TABLET, DESKTOP
    );
    CREATE INDEX IX_StoreVisitors_CustomerID ON StoreVisitors(CustomerID);
    CREATE INDEX IX_StoreVisitors_FirstSeenAt ON StoreVisitors(FirstSeenAt);
END
GO

-- StoreEvents (StoreEvent entity): one row per tracked action.
IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'StoreEvents')
BEGIN
    CREATE TABLE StoreEvents (
        EventID BIGINT IDENTITY(1,1) PRIMARY KEY,
        EventType VARCHAR(30) NOT NULL,              -- StoreEventType enum name
        VisitorID VARCHAR(36) NOT NULL,
        SessionID VARCHAR(36) NOT NULL,
        CustomerID INT NULL,
        ProductID INT NULL,
        VariantID INT NULL,
        Quantity INT NULL,
        OrderID INT NULL,
        OrderValue DECIMAL(12,2) NULL,               -- ORDER_PLACED only
        SearchQuery NVARCHAR(100) NULL,
        ResultCount INT NULL,
        VoucherCode NVARCHAR(50) NULL,
        VoucherResult VARCHAR(30) NULL,              -- APPLIED or the rejection reason
        PagePath NVARCHAR(200) NULL,
        Referrer NVARCHAR(300) NULL,
        UtmSource NVARCHAR(100) NULL,
        UtmMedium NVARCHAR(100) NULL,
        UtmCampaign NVARCHAR(100) NULL,
        DeviceType VARCHAR(10) NULL,
        OccurredAt DATETIME2 NOT NULL,               -- browser time, clamped by the server
        ReceivedAt DATETIME2 NOT NULL
    );
    CREATE INDEX IX_StoreEvents_OccurredAt ON StoreEvents(OccurredAt);
    CREATE INDEX IX_StoreEvents_Type_OccurredAt ON StoreEvents(EventType, OccurredAt);
    CREATE INDEX IX_StoreEvents_VisitorID ON StoreEvents(VisitorID);
    CREATE INDEX IX_StoreEvents_ProductID ON StoreEvents(ProductID) WHERE ProductID IS NOT NULL;
END
GO

-- StoreCarts (StoreCart entity): the latest cart of each visitor.
-- Items is JSON: [{"productId":1,"variantId":2,"quantity":1}, ...]
IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'StoreCarts')
BEGIN
    CREATE TABLE StoreCarts (
        VisitorID VARCHAR(36) NOT NULL PRIMARY KEY,
        CustomerID INT NULL,
        Items NVARCHAR(MAX) NOT NULL,
        ItemCount INT NOT NULL DEFAULT 0,
        CartValue DECIMAL(12,2) NOT NULL DEFAULT 0,  -- priced by the server
        Status VARCHAR(10) NOT NULL,                 -- ACTIVE, EMPTY, ORDERED
        OrderID INT NULL,
        UpdatedAt DATETIME2 NOT NULL,
        OrderedAt DATETIME2 NULL
    );
    CREATE INDEX IX_StoreCarts_Status_UpdatedAt ON StoreCarts(Status, UpdatedAt);
    CREATE INDEX IX_StoreCarts_CustomerID ON StoreCarts(CustomerID);
END
GO
