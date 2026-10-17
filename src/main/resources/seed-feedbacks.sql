-- =====================================================================
-- Seed Data: Customer Feedbacks & Comments (Arabic)
-- =====================================================================

SET NOCOUNT ON;

-- Clear previous seed if needed (optional)
-- DELETE FROM FeedbackComments;
-- DELETE FROM CustomerFeedbacks;

DECLARE @F1 INT, @F2 INT, @F3 INT, @F4 INT, @F5 INT, @F6 INT, @F7 INT, @F8 INT;

-- 1. General Featured Testimonial (Home Page)
INSERT INTO CustomerFeedbacks (
    CustomerID, ProductID, OrderID, CustomerName, CustomerEmail, CustomerPhone,
    FeedbackType, Subject, Comment, Rating, Status, IsPublic, IsFeatured,
    AdminReply, RepliedBy, RepliedAt, CreatedAt, UpdatedAt
) VALUES (
    39, NULL, NULL, N'ادم محمد الجروانى', N'first@gmail.com', N'01012345678',
    N'GENERAL', N'خامات ممتازة وتغليف راقي جداً',
    N'ما شاء الله الخامات ممتازة جداً والتغليف تحفة، وصلني الطلب في يومين بس في القاهرة. تجربة ممتازة وهكرر التعامل معاكم أكيد ❤️',
    5, N'APPROVED', 1, 1,
    N'شكراً جداً لثقتك في ورشة يا فندم! سعداء جداً بتجربتك ودايماً في خدمتك.',
    N'ekhalaf', DATEADD(hour, 2, GETDATE()), DATEADD(day, -3, GETDATE()), GETDATE()
);
SET @F1 = SCOPE_IDENTITY();

INSERT INTO FeedbackComments (FeedbackID, CustomerID, UserID, AuthorName, AuthorRole, CommentText, CreatedAt)
VALUES (@F1, NULL, 2, N'إدارة ورشة', N'ADMIN', N'شكراً جداً لثقتك في ورشة يا فندم! سعداء جداً بتجربتك ودايماً في خدمتك.', DATEADD(hour, 2, GETDATE()));

INSERT INTO FeedbackComments (FeedbackID, CustomerID, UserID, AuthorName, AuthorRole, CommentText, CreatedAt)
VALUES (@F1, 39, NULL, N'ادم محمد الجروانى', N'CUSTOMER', N'العفو يا فندم، التغليف شرفني قدام أصحابي لما قدمته كهدية.', DATEADD(hour, 4, GETDATE()));


-- 2. General Featured Review
INSERT INTO CustomerFeedbacks (
    CustomerID, ProductID, OrderID, CustomerName, CustomerEmail, CustomerPhone,
    FeedbackType, Subject, Comment, Rating, Status, IsPublic, IsFeatured,
    AdminReply, RepliedBy, RepliedAt, CreatedAt, UpdatedAt
) VALUES (
    40, NULL, NULL, N'عائشه الحلواني', N'aisha.elhelwany@example.com', N'01122334455',
    N'GENERAL', N'خدمة عملاء راقية وسرعة في التوصيل',
    N'خدمة العملاء متعاونين جداً وسريعين في الرد على الواتساب والموقع. المنتجات شكلها في الحقيقة أحلى وأفخم بكتير من الصور!',
    5, N'APPROVED', 1, 1,
    N'ألف شكر على كلامك الطيب يا أستاذة عائشة، هدفنا دايماً نقدّم أعلى جودة تليق بيكم.',
    N'ekhalaf', DATEADD(hour, 1, GETDATE()), DATEADD(day, -5, GETDATE()), GETDATE()
);
SET @F2 = SCOPE_IDENTITY();

INSERT INTO FeedbackComments (FeedbackID, CustomerID, UserID, AuthorName, AuthorRole, CommentText, CreatedAt)
VALUES (@F2, NULL, 2, N'إدارة ورشة', N'ADMIN', N'ألف شكر على كلامك الطيب يا أستاذة عائشة، هدفنا دايماً نقدّم أعلى جودة تليق بيكم.', DATEADD(hour, 1, GETDATE()));


-- 3. Product Review: اسوره ايه الكرسي اسود (ProductID: 20)
INSERT INTO CustomerFeedbacks (
    CustomerID, ProductID, OrderID, CustomerName, CustomerEmail, CustomerPhone,
    FeedbackType, Subject, Comment, Rating, Status, IsPublic, IsFeatured,
    AdminReply, RepliedBy, RepliedAt, CreatedAt, UpdatedAt
) VALUES (
    41, 20, NULL, N'ملك قمر', N'malak.qamar@example.com', N'01234567890',
    N'PRODUCT', N'حفر ليزر غاية في الدقة',
    N'الأسورة شكلها شيك جداً والحفر واضح ودقيق ومبتغيرش لون مع المية أو العطور. تحفة بجد وتستاهل كل مليم!',
    5, N'APPROVED', 1, 1,
    NULL, NULL, NULL, DATEADD(day, -4, GETDATE()), GETDATE()
);
SET @F3 = SCOPE_IDENTITY();


-- 4. Another Review for اسوره ايه الكرسي اسود (ProductID: 20)
INSERT INTO CustomerFeedbacks (
    CustomerID, ProductID, OrderID, CustomerName, CustomerEmail, CustomerPhone,
    FeedbackType, Subject, Comment, Rating, Status, IsPublic, IsFeatured,
    AdminReply, RepliedBy, RepliedAt, CreatedAt, UpdatedAt
) VALUES (
    1039, 20, NULL, N'إسلام جابر', N'eslam.gaber@example.com', N'01555554433',
    N'PRODUCT', N'هدية قيمة جداً وفينش ممتاز',
    N'اشتريتها هدية وعجبت خطيبتي جداً، البوكس شيك والتفاصيل ممتازة ومريحة في اللبس.',
    5, N'APPROVED', 1, 0,
    NULL, NULL, NULL, DATEADD(day, -2, GETDATE()), GETDATE()
);
SET @F4 = SCOPE_IDENTITY();


-- 5. Review for أسورة اية الكرسي جولد (ProductID: 22)
INSERT INTO CustomerFeedbacks (
    CustomerID, ProductID, OrderID, CustomerName, CustomerEmail, CustomerPhone,
    FeedbackType, Subject, Comment, Rating, Status, IsPublic, IsFeatured,
    AdminReply, RepliedBy, RepliedAt, CreatedAt, UpdatedAt
) VALUES (
    42, 22, NULL, N'شيماء حسام', N'shaimaa.hossam@example.com', N'01099887766',
    N'PRODUCT', N'اللون الدهبي ثابت وراقي',
    N'اللون الدهبي بتاعها ثابت ومش بيقشر خالص بعد استعمال شهرين متواصلين. برافو بجد على الأمانة في الخامات.',
    5, N'APPROVED', 1, 1,
    N'شهادة نعتز بيها يا فندم، بنستخدم أفضل معايير الطلاء الكهربائي المقاوم للصدأ.',
    N'ekhalaf', DATEADD(hour, 3, GETDATE()), DATEADD(day, -6, GETDATE()), GETDATE()
);
SET @F5 = SCOPE_IDENTITY();

INSERT INTO FeedbackComments (FeedbackID, CustomerID, UserID, AuthorName, AuthorRole, CommentText, CreatedAt)
VALUES (@F5, NULL, 2, N'إدارة ورشة', N'ADMIN', N'شهادة نعتز بيها يا فندم، بنستخدم أفضل معايير الطلاء الكهربائي المقاوم للصدأ.', DATEADD(hour, 3, GETDATE()));


-- 6. Review for انسيال كابلز عقدة (ProductID: 25) with thread
INSERT INTO CustomerFeedbacks (
    CustomerID, ProductID, OrderID, CustomerName, CustomerEmail, CustomerPhone,
    FeedbackType, Subject, Comment, Rating, Status, IsPublic, IsFeatured,
    AdminReply, RepliedBy, RepliedAt, CreatedAt, UpdatedAt
) VALUES (
    NULL, 25, NULL, N'كريم عبد العزيز', N'karim.azeez@example.com', N'01155443322',
    N'PRODUCT', N'أحلى هدية كابلز',
    N'أحلى هدية كابلز جبتها، الخيط متين ومبتفكش والشكل فخم جداً وبسيط في نفس الوقت.',
    5, N'APPROVED', 1, 0,
    NULL, NULL, NULL, DATEADD(day, -1, GETDATE()), GETDATE()
);
SET @F6 = SCOPE_IDENTITY();

INSERT INTO FeedbackComments (FeedbackID, CustomerID, UserID, AuthorName, AuthorRole, CommentText, CreatedAt)
VALUES (@F6, NULL, NULL, N'كريم عبد العزيز', N'CUSTOMER', N'ممكن أعرف لو اتغسلت بمية وصابون هل الخيط أو المعدن بيتأثروا؟', DATEADD(minute, 30, GETDATE()));

INSERT INTO FeedbackComments (FeedbackID, CustomerID, UserID, AuthorName, AuthorRole, CommentText, CreatedAt)
VALUES (@F6, NULL, 2, N'فريق الدعم الفني', N'ADMIN', N'أهلاً بحضرتك يا فندم، المعدن استانلس ستيل عيار 316L مقاوم للماء والصدأ تماماً، والخيط معالج ضد الماء.', DATEADD(minute, 45, GETDATE()));


-- 7. Suggestion Feedback
INSERT INTO CustomerFeedbacks (
    CustomerID, ProductID, OrderID, CustomerName, CustomerEmail, CustomerPhone,
    FeedbackType, Subject, Comment, Rating, Status, IsPublic, IsFeatured,
    AdminReply, RepliedBy, RepliedAt, CreatedAt, UpdatedAt
) VALUES (
    NULL, NULL, NULL, N'نورهان يحيى', N'nourhan.yahia@example.com', N'01011223344',
    N'SUGGESTION', N'إضافة كروت إهداء بالاسم في صفحة الدفع',
    N'اقتراح: ياريت تضيفوا خيارات تغليف هدايا مخصصة مع كروت إهداء بالاسم مباشرة من الويب سايت أثناء إتمام الطلب.',
    5, N'APPROVED', 1, 0,
    N'اقتراح رائع ومحل اهتمامنا! بنعمل حالياً على إضافة ميزة اختيار كارت الإهداء وتخصيص الرسالة في صفحة الدفع قريباً جداً.',
    N'ekhalaf', DATEADD(hour, 5, GETDATE()), DATEADD(day, -7, GETDATE()), GETDATE()
);
SET @F7 = SCOPE_IDENTITY();

INSERT INTO FeedbackComments (FeedbackID, CustomerID, UserID, AuthorName, AuthorRole, CommentText, CreatedAt)
VALUES (@F7, NULL, 2, N'إدارة التطوير', N'ADMIN', N'اقتراح رائع ومحل اهتمامنا! بنعمل حالياً على إضافة ميزة اختيار كارت الإهداء وتخصيص الرسالة في صفحة الدفع قريباً جداً.', DATEADD(hour, 5, GETDATE()));


-- 8. Recent Pending Feedback (to demonstrate Admin Moderation in ERP)
INSERT INTO CustomerFeedbacks (
    CustomerID, ProductID, OrderID, CustomerName, CustomerEmail, CustomerPhone,
    FeedbackType, Subject, Comment, Rating, Status, IsPublic, IsFeatured,
    AdminReply, RepliedBy, RepliedAt, CreatedAt, UpdatedAt
) VALUES (
    NULL, 21, NULL, N'مريم سامي', N'mariam.sami@example.com', N'01277665544',
    N'PRODUCT', N'استفسار عن المقاس للأسورة السيلفر',
    N'الأسورة شكلها يجنن بس كان نفسي تكون أضيق شوية عشان إيدي رفيعة. هل ممكن تتضيق؟',
    4, N'PENDING', 0, 0,
    NULL, NULL, NULL, GETDATE(), GETDATE()
);
SET @F8 = SCOPE_IDENTITY();

PRINT 'Successfully seeded Arabic feedbacks and comments.';
