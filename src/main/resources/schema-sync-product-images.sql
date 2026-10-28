-- =====================================================================
-- Warsha Backend: extra gallery images per product
-- Safe to run more than once. Run after schema-sync-variants.sql.
-- The product's main (cover) image stays in Products.ImageUrl.
-- =====================================================================

IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'ProductImages')
BEGIN
    CREATE TABLE ProductImages (
        ImageID INT IDENTITY(1,1) PRIMARY KEY,
        ProductID INT NOT NULL,
        ImageUrl NVARCHAR(500) NOT NULL,             -- e.g. /images/<uuid>.webp
        SortOrder INT NOT NULL DEFAULT 0,
        CreatedAt DATETIME NOT NULL DEFAULT GETDATE(),
        CONSTRAINT FK_ProductImages_Products FOREIGN KEY (ProductID) REFERENCES Products(ProductID)
    );
    CREATE INDEX IX_ProductImages_ProductID ON ProductImages(ProductID);
END
GO
