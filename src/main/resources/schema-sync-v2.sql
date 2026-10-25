-- =====================================================================
-- Warsha Backend: sync WarshaDB with the JPA entities (ddl-auto=none)
-- Safe to run more than once. Feedback tables live in schema-feedbacks.sql.
-- =====================================================================

-- Customers: OTP for email verification (Customer.otp / otpExpirationTime)
IF COL_LENGTH('Customers', 'Otp') IS NULL
    ALTER TABLE Customers ADD Otp NVARCHAR(10) NULL;
GO
IF COL_LENGTH('Customers', 'OtpExpirationTime') IS NULL
    ALTER TABLE Customers ADD OtpExpirationTime DATETIME NULL;
GO

-- ShippingZones (ShippingZone entity)
IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'ShippingZones')
BEGIN
    CREATE TABLE ShippingZones (
        Id INT IDENTITY(1,1) PRIMARY KEY,
        CityName NVARCHAR(100) NOT NULL,
        ShippingFee DECIMAL(10,2) NOT NULL,
        IsActive BIT NOT NULL DEFAULT 1,
        CreatedAt DATETIME NOT NULL DEFAULT GETDATE(),
        UpdatedAt DATETIME NOT NULL DEFAULT GETDATE(),
        UpdatedBy NVARCHAR(50) NULL
    );
END
GO

-- PromoCodes (Voucher entity)
IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'PromoCodes')
BEGIN
    CREATE TABLE PromoCodes (
        PromoID BIGINT IDENTITY(1,1) PRIMARY KEY,
        Code NVARCHAR(50) NOT NULL UNIQUE,
        DiscountType NVARCHAR(20) NOT NULL,          -- PERCENTAGE, FIXED
        DiscountValue DECIMAL(10,2) NOT NULL,
        MinOrderAmount DECIMAL(10,2) NULL DEFAULT 0,
        MaxDiscountAmount DECIMAL(10,2) NULL,
        StartDate DATE NOT NULL,
        EndDate DATE NULL,
        IsActive BIT NOT NULL DEFAULT 1,
        MaxUsageLimit INT NULL,
        CurrentUsageCount INT NOT NULL DEFAULT 0
    );
END
GO
