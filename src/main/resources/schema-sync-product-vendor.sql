-- =====================================================================
-- Warsha Backend: products remember the vendor they are bought from.
-- Optional link (NULL = no vendor). Safe to run more than once.
-- =====================================================================

IF COL_LENGTH('Products', 'VendorID') IS NULL
    ALTER TABLE Products ADD VendorID INT NULL;  -- same type as Vendors.VendorID
GO

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = 'FK_Products_Vendors')
    ALTER TABLE Products ADD CONSTRAINT FK_Products_Vendors
        FOREIGN KEY (VendorID) REFERENCES Vendors(VendorID);
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_Products_VendorID' AND object_id = OBJECT_ID('Products'))
    CREATE INDEX IX_Products_VendorID ON Products(VendorID);
GO
