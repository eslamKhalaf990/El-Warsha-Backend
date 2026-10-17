-- =====================================================================
-- Warsha Backend: Customer Feedbacks & Comments Schema Migration
-- Compatible with Microsoft SQL Server 2019 / 2022
-- =====================================================================

IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'CustomerFeedbacks')
BEGIN
    CREATE TABLE CustomerFeedbacks (
        FeedbackID INT IDENTITY(1,1) PRIMARY KEY,
        CustomerID INT NULL,
        ProductID INT NULL,
        OrderID INT NULL,
        CustomerName NVARCHAR(150) NOT NULL,
        CustomerEmail NVARCHAR(150) NULL,
        CustomerPhone NVARCHAR(50) NULL,
        FeedbackType NVARCHAR(50) NOT NULL DEFAULT 'GENERAL', -- GENERAL, SERVICE, PRODUCT, ORDER, SUGGESTION, COMPLAINT
        Subject NVARCHAR(255) NULL,
        Comment NVARCHAR(MAX) NOT NULL,
        Rating INT NOT NULL CHECK (Rating >= 1 AND Rating <= 5),
        Status NVARCHAR(50) NOT NULL DEFAULT 'PENDING',        -- PENDING, APPROVED, REJECTED, RESOLVED
        IsPublic BIT NOT NULL DEFAULT 0,
        IsFeatured BIT NOT NULL DEFAULT 0,
        AdminReply NVARCHAR(MAX) NULL,
        RepliedBy NVARCHAR(100) NULL,
        RepliedAt DATETIME NULL,
        CreatedAt DATETIME NOT NULL DEFAULT GETDATE(),
        UpdatedAt DATETIME NOT NULL DEFAULT GETDATE(),
        CONSTRAINT FK_CustomerFeedbacks_Customer FOREIGN KEY (CustomerID) REFERENCES Customers(CustomerID) ON DELETE SET NULL,
        CONSTRAINT FK_CustomerFeedbacks_Product FOREIGN KEY (ProductID) REFERENCES Products(ProductID) ON DELETE SET NULL,
        CONSTRAINT FK_CustomerFeedbacks_Order FOREIGN KEY (OrderID) REFERENCES Orders(OrderID) ON DELETE SET NULL
    );

    CREATE INDEX IX_CustomerFeedbacks_Status ON CustomerFeedbacks(Status);
    CREATE INDEX IX_CustomerFeedbacks_ProductID ON CustomerFeedbacks(ProductID);
    CREATE INDEX IX_CustomerFeedbacks_CustomerID ON CustomerFeedbacks(CustomerID);
    CREATE INDEX IX_CustomerFeedbacks_CreatedAt ON CustomerFeedbacks(CreatedAt);
    PRINT 'CustomerFeedbacks table created successfully.';
END
ELSE
BEGIN
    PRINT 'CustomerFeedbacks table already exists.';
END
GO

IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'FeedbackComments')
BEGIN
    CREATE TABLE FeedbackComments (
        CommentID INT IDENTITY(1,1) PRIMARY KEY,
        FeedbackID INT NOT NULL,
        CustomerID INT NULL,
        UserID INT NULL,
        AuthorName NVARCHAR(150) NOT NULL,
        AuthorRole NVARCHAR(50) NOT NULL DEFAULT 'CUSTOMER',  -- CUSTOMER, ADMIN
        CommentText NVARCHAR(MAX) NOT NULL,
        CreatedAt DATETIME NOT NULL DEFAULT GETDATE(),
        CONSTRAINT FK_FeedbackComments_Feedback FOREIGN KEY (FeedbackID) REFERENCES CustomerFeedbacks(FeedbackID) ON DELETE CASCADE,
        CONSTRAINT FK_FeedbackComments_Customer FOREIGN KEY (CustomerID) REFERENCES Customers(CustomerID) ON DELETE SET NULL,
        CONSTRAINT FK_FeedbackComments_User FOREIGN KEY (UserID) REFERENCES Users(Id) ON DELETE SET NULL
    );

    CREATE INDEX IX_FeedbackComments_FeedbackID ON FeedbackComments(FeedbackID);
    PRINT 'FeedbackComments table created successfully.';
END
ELSE
BEGIN
    PRINT 'FeedbackComments table already exists.';
END
GO
