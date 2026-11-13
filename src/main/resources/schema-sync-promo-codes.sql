-- =====================================================================
-- Warsha Backend: store orders remember the promo code they used.
-- The discount itself is already part of Orders.Discount; this column
-- only records which code gave it. Safe to run more than once.
-- =====================================================================

IF COL_LENGTH('Orders', 'PromoCode') IS NULL
    ALTER TABLE Orders ADD PromoCode NVARCHAR(50) NULL;
GO
