-- =====================================================================
-- Warsha Backend: ShippingZones is the only source of delivery prices.
-- Fills in any governorate that has no zone yet, with the prices the
-- backend used to have in code. Existing zones (and prices already edited
-- in the ERP) are never touched. Safe to run more than once.
-- =====================================================================

;WITH Seed (CityName, ShippingFee) AS (
    SELECT N'القاهرة', 80 UNION ALL SELECT N'الجيزة', 80 UNION ALL
    SELECT N'القليوبية', 100 UNION ALL SELECT N'المنوفية', 100 UNION ALL
    SELECT N'الشرقية', 100 UNION ALL SELECT N'الغربية', 100 UNION ALL
    SELECT N'الإسكندرية', 90 UNION ALL SELECT N'البحيرة', 100 UNION ALL
    SELECT N'الدقهلية', 100 UNION ALL SELECT N'كفر الشيخ', 100 UNION ALL
    SELECT N'الإسماعيلية', 100 UNION ALL SELECT N'السويس', 100 UNION ALL
    SELECT N'بورسعيد', 100 UNION ALL SELECT N'الفيوم', 110 UNION ALL
    SELECT N'بني سويف', 110 UNION ALL SELECT N'المنيا', 110 UNION ALL
    SELECT N'دمياط', 100 UNION ALL SELECT N'دمياط الجديدة', 100 UNION ALL
    SELECT N'أسيوط', 120 UNION ALL SELECT N'شمال سيناء', 130 UNION ALL
    SELECT N'جنوب سيناء', 130 UNION ALL SELECT N'سوهاج', 120 UNION ALL
    SELECT N'البحر الأحمر', 120 UNION ALL SELECT N'مطروح', 130 UNION ALL
    SELECT N'قنا', 120 UNION ALL SELECT N'الأقصر', 120 UNION ALL
    SELECT N'أسوان', 120 UNION ALL SELECT N'الوادي الجديد', 130
)
INSERT INTO ShippingZones (CityName, ShippingFee, IsActive, CreatedAt, UpdatedAt, UpdatedBy)
SELECT s.CityName, s.ShippingFee, 1, GETDATE(), GETDATE(), N'seed'
FROM Seed s
WHERE NOT EXISTS (SELECT 1 FROM ShippingZones z WHERE LTRIM(RTRIM(z.CityName)) = s.CityName);
GO
