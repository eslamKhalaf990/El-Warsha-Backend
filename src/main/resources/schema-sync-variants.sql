-- =====================================================================
-- Warsha Backend: product variants (size / color) and Arabic product text
-- Safe to run more than once. Run after schema-sync-v2.sql.
-- =====================================================================

-- Products: Arabic name and description (NVARCHAR keeps Arabic characters)
IF COL_LENGTH('Products', 'NameAr') IS NULL
    ALTER TABLE Products ADD NameAr NVARCHAR(255) NULL;
GO
IF COL_LENGTH('Products', 'DescriptionAr') IS NULL
    ALTER TABLE Products ADD DescriptionAr NVARCHAR(MAX) NULL;
GO

-- ProductVariants (ProductVariant entity): one row per size/color combination.
-- Price is NULL when the variant sells at the product's price.
IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'ProductVariants')
BEGIN
    CREATE TABLE ProductVariants (
        VariantID INT IDENTITY(1,1) PRIMARY KEY,
        ProductID INT NOT NULL,
        Size NVARCHAR(50) NULL,
        Color NVARCHAR(50) NULL,
        ColorAr NVARCHAR(50) NULL,
        ColorHex NVARCHAR(9) NULL,                   -- e.g. #1A1A1A
        SKU NVARCHAR(100) NULL,
        SellingPrice FLOAT NULL,
        Quantity INT NOT NULL DEFAULT 0,
        Sold INT NOT NULL DEFAULT 0,
        ImageUrl NVARCHAR(500) NULL,
        IsDeleted BIT NOT NULL DEFAULT 0,
        CreatedAt DATETIME NOT NULL DEFAULT GETDATE(),
        UpdatedAt DATETIME NULL,
        CONSTRAINT FK_ProductVariants_Products FOREIGN KEY (ProductID) REFERENCES Products(ProductID)
    );
    CREATE INDEX IX_ProductVariants_ProductID ON ProductVariants(ProductID);
END
GO

-- OrderItems: which variant was ordered (NULL for products without variants)
IF COL_LENGTH('OrderItems', 'VariantID') IS NULL
    ALTER TABLE OrderItems ADD VariantID INT NULL;
GO
IF NOT EXISTS (SELECT * FROM sys.foreign_keys WHERE name = 'FK_OrderItems_ProductVariants')
    ALTER TABLE OrderItems ADD CONSTRAINT FK_OrderItems_ProductVariants
        FOREIGN KEY (VariantID) REFERENCES ProductVariants(VariantID);
GO
