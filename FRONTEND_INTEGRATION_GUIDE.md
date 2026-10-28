# Frontend Integration Guide — Warsha E-Commerce & ERP System

This document provides complete instructions and API documentation for the frontend team to integrate a customer-facing e-commerce web application with the Warsha backend system.

---

## 📋 Table of Contents
1. [General API Conventions & Architecture](#1-general-api-conventions--architecture)
2. [Customer Authentication & Account Management](#2-customer-authentication--account-management)
   - [Customer Registration (Sign Up)](#21-customer-registration-sign-up)
   - [OTP Verification](#22-otp-verification)
   - [Resend Verification OTP](#23-resend-verification-otp)
   - [Customer Login](#24-customer-login)
   - [Forgot Password & Reset](#25-forgot-password--reset)
   - [Change Password](#26-change-password)
3. [Product Catalog & Categories](#3-product-catalog--categories)
   - [Get All Categories](#31-get-all-categories)
   - [Get All Products](#32-get-all-products)
   - [Get Products by Category](#33-get-products-by-category)
   - [Get Product by ID](#34-get-product-by-id)
   - [Serving Images](#35-product-images)
   - [Sizes & Colors (Variants)](#36-sizes--colors-variants)
   - [Arabic Name & Description](#37-arabic-name--description)
   - [Image Gallery](#38-image-gallery)
4. [Shipping & Governorates](#4-shipping--governorates)
   - [Get All Governorates & Prices (`GET /governorates`)](#41-get-all-governorates--prices)
   - [Get Governorates as Key-Value Map (`GET /governorates/map`)](#42-get-governorates-as-key-value-map)
   - [Get Delivery Price for a Specific Governorate (`GET /governorates/price`)](#43-get-delivery-price-for-a-specific-governorate)
5. [E-Commerce Order Placement](#5-e-commerce-order-placement)
   - [Placing an Order (`POST /orders/placeOrder`)](#51-placing-an-order)
   - [Fetching Customer Order History (`GET /orders/customer`)](#52-fetching-customer-order-history)
6. [Customer Feedbacks, Reviews & Comments](#6-customer-feedbacks-reviews--comments)
   - [Submitting Feedback (`POST /feedbacks`)](#61-submitting-feedback-post-feedbacks)
   - [Fetching Public Approved Feedbacks & Testimonials (`GET /feedbacks/public`)](#62-fetching-public-approved-feedbacks--testimonials-get-feedbackspublic)
   - [Product Reviews & Rating Summary (`GET /feedbacks/product/{productId}`)](#63-product-reviews--rating-summary-get-feedbacksproductproductid)
   - [Fetching Customer's Own Feedbacks (`GET /feedbacks/customer`)](#64-fetching-customers-own-feedbacks-get-feedbackscustomer)
   - [Getting Single Feedback by ID (`GET /feedbacks/{id}`)](#65-getting-single-feedback-by-id-get-feedbacksid)
   - [Adding a Comment / Reply to a Feedback Thread (`POST /feedbacks/{id}/comments`)](#66-adding-a-comment--reply-to-a-feedback-thread-post-feedbacksidcomments)
   - [Admin ERP Feedback Moderation Endpoints](#67-admin-erp-feedback-moderation-endpoints)
7. [Frontend Integration Workflow & Code Examples](#7-frontend-integration-workflow--code-examples)
   - [Axios Instance & Interceptor Setup](#71-axios-instance--interceptor-setup)
   - [Feedback API Helper (TypeScript)](#72-feedback-api-helper-typescript)
   - [Full Checkout & Order Placement Helper](#73-full-checkout--order-placement-helper)
8. [Common Error Responses & Status Codes](#8-common-error-responses--status-codes)

---

## 1. General API Conventions & Architecture

- **Base URL**: `http://localhost:8080` (Development) or your deployed domain (e.g., `https://api.yourdomain.com`).
- **Security & JWT**:
  - Stateless JWT authentication is used.
  - Protected endpoints require an `Authorization` header formatted as:
    ```http
    Authorization: Bearer <jwt_token>
    ```
- **Public vs. Protected Endpoints**:
  - **Public (No token needed)**:
    - `/auth/**` (Customer login, verification, password resets)
    - `/customers/customerSignUp`
    - `/customers/resend-otp`
    - `/governorates/**` and `GET /shipping/**` (Governorates & delivery prices)
    - `/category/**` (Read operations)
    - `/products/**` (Read operations & image retrieval)
    - `POST /feedbacks` (Submission works for both guests & logged-in customers)
    - `GET /feedbacks/public/**` (Approved testimonials & reviews)
    - `GET /feedbacks/product/**` (Product ratings & reviews)
    - `GET /feedbacks/{id}` (Read feedback details & replies)
    - `POST /feedbacks/{id}/comments` (Add a comment)
  - **Protected (Customer Bearer Token required)**:
    - `/orders/placeOrder`
    - `/orders/customer`
    - `/feedbacks/customer` (View logged-in customer's feedback history)

---

## 2. Customer Authentication & Account Management

### 2.1 Customer Registration (Sign Up)

Registers a new customer. Upon registration, the customer's account status is set to `Pending` and a 6-digit OTP code is automatically dispatched to their email.

- **Endpoint**: `POST /customers/customerSignUp`
- **Content-Type**: `application/json`
- **Auth Required**: No

#### Request Body
```json
{
  "fullName": "Ahmed Mohamed",
  "email": "ahmed.mohamed@example.com",
  "password": "StrongPassword123!",
  "phone": "01012345678",
  "secondaryPhone": "01187654321",
  "governorate": "القاهرة",
  "city": "Nasr City",
  "address": "12 Abbas El Akkad St, Apt 4"
}
```

> **Note on `governorate`**: Must match one of the recognized Egyptian governorates (see [Section 4](#4-shipping--governorates)).

#### Response
- **Status `201 Created`**:
```text
Registration successful. Please check your email for the verification code.
```
- **Status `400 Bad Request`**:
```text
Registration failed: <error details>
```

---

### 2.2 OTP Verification

Verifies the customer's OTP code sent via email, transitioning their status from `Pending` to `Active`.

- **Endpoint**: `POST /auth/verifyCode`
- **Content-Type**: `application/json`
- **Auth Required**: No

#### Request Body
```json
{
  "email": "ahmed.mohamed@example.com",
  "otp": "123456"
}
```

#### Response
- **Status `200 OK`**:
```text
Account verified successfully
```
- **Status `400 Bad Request`**:
```text
Invalid or expired OTP
```

---

### 2.3 Resend Verification OTP

Resends a fresh OTP email if the user didn't receive the original one or if the code expired (OTP lifetime is 10 minutes).

- **Endpoint**: `POST /customers/resend-otp?email={email}`
- **Auth Required**: No

#### Request
```http
POST /customers/resend-otp?email=ahmed.mohamed@example.com
```

#### Response
- **Status `200 OK`**:
```text
OTP resent successfully.
```

---

### 2.4 Customer Login

Authenticates active customer credentials and returns a JWT token along with customer profile details.

- **Endpoint**: `POST /auth/customerLogin`
- **Content-Type**: `application/json`
- **Auth Required**: No

#### Request Body
```json
{
  "username": "ahmed.mohamed@example.com",
  "password": "StrongPassword123!"
}
```

#### Responses
- **Status `200 OK` (Success)**:
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "address": "12 Abbas El Akkad St, Apt 4",
  "name": "Ahmed Mohamed",
  "phone": "01012345678",
  "email": "ahmed.mohamed@example.com",
  "governorate": "القاهرة"
}
```
> **Frontend Action**: Store `token` in `localStorage` / secure cookies and attach it as `Authorization: Bearer <token>` for all authenticated customer requests.

- **Status `403 Forbidden` (Account is Pending verification)**:
```text
Account needs verification. OTP sent to email.
```
> **Frontend Action**: If `403` is received with this message, automatically redirect the user to the OTP verification screen.

- **Status `401 Unauthorized`**:
```text
Invalid customer credentials
```

---

### 2.5 Forgot Password & Reset

#### Step 1: Request Password Reset OTP
- **Endpoint**: `POST /auth/forgotPassword`
- **Content-Type**: `application/json`

**Request Body**:
```json
{
  "email": "ahmed.mohamed@example.com"
}
```

**Response `200 OK`**: `"OTP sent to email"`

#### Step 2: Submit OTP & New Password
- **Endpoint**: `POST /auth/resetPassword`
- **Content-Type**: `application/json`

**Request Body**:
```json
{
  "email": "ahmed.mohamed@example.com",
  "otp": "123456",
  "newPassword": "MyNewSecurePassword456!"
}
```

**Response `200 OK`**: `"Password reset successfully"`

---

### 2.6 Change Password

For logged-in customers wishing to update their password.

- **Endpoint**: `POST /auth/changePassword`
- **Content-Type**: `application/json`

#### Request Body
```json
{
  "email": "ahmed.mohamed@example.com",
  "oldPassword": "StrongPassword123!",
  "newPassword": "MyNewSecurePassword456!"
}
```

---

## 3. Product Catalog & Categories

### 3.1 Get All Categories

- **Endpoint**: `GET /category`
- **Auth Required**: No

#### Response `200 OK`
```json
[
  {
    "categoryId": 1,
    "name": "Wooden Tables"
  },
  {
    "categoryId": 2,
    "name": "Chairs & Armchairs"
  }
]
```

---

### 3.2 Get All Products

Fetches all non-deleted products with their stock, pricing, category metadata, Arabic text and sizes/colors.

- **Endpoint**: `GET /products`
- **Auth Required**: No

#### Response `200 OK`
```json
[
  {
    "id": 15,
    "name": "Linen Shirt",
    "nameAr": "قميص كتان",
    "description": "Relaxed fit linen shirt",
    "descriptionAr": "قميص كتان قصة واسعة",
    "buyingPrice": 250.0,
    "sellingPrice": 450.0,
    "discount": 50.0,
    "totalPrice": 400.0,
    "imageUrl": "/images/a1b2c3d4-e5f6-7890-abcd-1234567890ab.jpg",
    "sku": "SHR-LIN-001",
    "quantity": "12",
    "categoryId": 1,
    "categoryName": "Shirts",
    "deleted": null,
    "deletedAt": null,
    "hasVariants": true,
    "variants": [
      {
        "id": 31,
        "productId": 15,
        "size": "M",
        "color": "Black",
        "colorAr": "أسود",
        "colorHex": "#1A1A1A",
        "sku": "SHR-LIN-001-M-BLK",
        "sellingPrice": null,
        "price": 450.0,
        "totalPrice": 400.0,
        "quantity": 5,
        "inStock": true,
        "imageUrl": null
      },
      {
        "id": 32,
        "productId": 15,
        "size": "XL",
        "color": "Black",
        "colorAr": "أسود",
        "colorHex": "#1A1A1A",
        "sku": null,
        "sellingPrice": 500.0,
        "price": 500.0,
        "totalPrice": 450.0,
        "quantity": 7,
        "inStock": true,
        "imageUrl": null
      }
    ],
    "images": [
      { "id": 4, "imageUrl": "/images/5b2c1e9a-back.webp", "sortOrder": 0 },
      { "id": 7, "imageUrl": "/images/91d0a3f4-detail.webp", "sortOrder": 1 }
    ]
  }
]
```

> **New fields (backwards compatible — old clients can ignore them):**
> - `nameAr`, `descriptionAr` — Arabic text, `null` until the admin fills it in. Fall back to `name` / `description`.
> - `hasVariants` — `true` when the product is sold in sizes/colors.
> - `variants` — active sizes/colors (always an array, `[]` for simple products). See [3.6](#36-sizes--colors-variants).
> - `images` — extra gallery images, already in display order (always an array). See [3.8](#38-image-gallery).
> - `discount` and `totalPrice` are now also filled on `GET /products/getByCategoryId`.

---

### 3.3 Get Products by Category

- **Endpoint**: `GET /products/getByCategoryId?categoryId={id}`
- **Auth Required**: No

#### Request
```http
GET /products/getByCategoryId?categoryId=1
```

#### Response `200 OK`
Returns an array of `ProductDTO` matching the specified `categoryId`.

---

### 3.4 Get Product by ID

- **Endpoint**: `GET /products/{id}`
- **Auth Required**: No

#### Response `200 OK`
Returns one `ProductDTO` — the same shape as an item of `GET /products`, including `nameAr`, `descriptionAr`, `hasVariants` and `variants`.

> ⚠️ **Changed:** this endpoint used to return the raw product entity (keys like `productID`, nested `category`). It now returns the DTO (`id`, `categoryId`, `categoryName`, …). Clients reading `productID` must switch to `id`.

---

### 3.5 Product Images

Images are stored and referenced via relative paths returned in `imageUrl` (e.g. `/images/filename.jpg`).
- The backend serves them directly: `GET /images/<filename>` (prefix `imageUrl` with the API base URL).
- Alternatively, via backend endpoint:
  ```http
  GET /products/getImage?filename=<unique_filename>
  ```

#### Image library (choose an existing image for a product)

All images live in one shared folder on the server (`warsha_images`). Replacing or deleting a product never deletes the image file.

**List all images**
```http
GET /products/images
```
```json
[
  { "filename": "031a44dc-d0a9-4caf-8e74-0be162b081c8.jpg", "url": "/images/031a44dc-d0a9-4caf-8e74-0be162b081c8.jpg" }
]
```

**Assign an image to a product**
```http
PUT /products/{id}/image
Content-Type: application/json

{ "filename": "031a44dc-d0a9-4caf-8e74-0be162b081c8.jpg" }
```
Returns the updated product (`200`). Errors: `400` missing/invalid filename, `404` image or product not found.

**Upload a new image to the library** (without assigning it)
```http
POST /products/images
Content-Type: multipart/form-data

image: <file>
```
Returns `201` with `{ "filename": "...", "url": "/images/..." }`. Then assign it with `PUT /products/{id}/image`.

---

### 3.6 Sizes & Colors (Variants)

A product can be sold in several sizes and/or colors. Each combination is a **variant** with its own stock and, optionally, its own price.

**Rules**
- A variant has a `size`, a `color`, or both.
- `sellingPrice` on a variant is optional. When it is `null` the variant sells at the product's `sellingPrice`. `price` is always the effective price before discount; `totalPrice` is after the product's `discount`.
- When a product has variants, its `quantity` is **the total of its variants** and is maintained by the server. Edits to the product's own `quantity` are ignored while it has variants.
- Deleting a variant is a soft delete; old orders keep showing it.
- Products without variants work exactly as before.

| Method | Endpoint | Auth | Body | Response |
| :--- | :--- | :--- | :--- | :--- |
| `GET` | `/products/{id}/variants` | Public | — | `200` array of variants |
| `POST` | `/products/{id}/variants` | Admin | `ProductVariantRequest` | `201` the new variant |
| `PUT` | `/products/{id}/variants/{variantId}` | Admin | `ProductVariantRequest` | `200` the updated variant |
| `DELETE` | `/products/{id}/variants/{variantId}` | Admin | — | `204` |

#### `ProductVariantRequest`
```json
{
  "size": "XL",
  "color": "Black",
  "colorAr": "أسود",
  "colorHex": "#1A1A1A",
  "sku": "SHR-LIN-001-XL-BLK",
  "sellingPrice": 500.0,
  "quantity": 7
}
```

| Field | Required | Notes |
| :--- | :--- | :--- |
| `size` | one of size/color | max 50 chars, e.g. `M`, `42`, `30x40` |
| `color` | one of size/color | max 50 chars, English name |
| `colorAr` | no | Arabic color name |
| `colorHex` | no | `#RRGGBB` or `#RRGGBBAA`, for swatches |
| `sku` | no | max 100 chars |
| `sellingPrice` | no | `null` = use the product's price; must be ≥ 0 |
| `quantity` | **yes** | stock, ≥ 0 |

#### Errors (standard `ApiError` body)
| Status | `code` | When |
| :--- | :--- | :--- |
| `400` | `VALIDATION_FAILED` | No size and no color, negative stock/price, bad color code |
| `404` | `NOT_FOUND` | Product or variant doesn't exist (or variant belongs to another product) |
| `409` | `CONFLICT` | The product already has a variant with the same size + color |

---

### 3.7 Arabic Name & Description

`nameAr` and `descriptionAr` are sent inside the existing `product` JSON part of the admin create/update calls:

```http
POST /products            (multipart: product=<json>, image=<file>)
PUT  /products/{id}       (multipart: product=<json>, image=<file, optional>)
```
```json
{
  "name": "Linen Shirt",
  "nameAr": "قميص كتان",
  "description": "Relaxed fit linen shirt",
  "descriptionAr": "قميص كتان قصة واسعة",
  "buyingPrice": 250,
  "sellingPrice": 450,
  "discount": 50,
  "category": { "categoryId": 1 },
  "quantity": "12"
}
```

**Display rule for clients:** show `nameAr` / `descriptionAr` when the UI is in Arabic and the value is not empty; otherwise show `name` / `description`.

---

### 3.8 Image Gallery

A product can have up to **10 extra images** besides its cover. The cover stays in `imageUrl`; the extras come in `images`, already sorted.

**What to show:** swipe through `[imageUrl, ...images.map(i => i.imageUrl)]`, cover first. Image URLs are relative (`/images/<file>`), exactly like `imageUrl`.

Files live in the shared image library (`GET /products/images`), so removing an image from a gallery never deletes the file.

| Method | Endpoint | Auth | Body | Response |
| :--- | :--- | :--- | :--- | :--- |
| `GET` | `/products/{id}/images` | Public | — | `200` array of `{id, imageUrl, sortOrder}` |
| `POST` | `/products/{id}/images/upload` | Admin | multipart, file part `image` | `201` the new image |
| `POST` | `/products/{id}/images` | Admin | `{ "filename": "abc.webp" }` (an image already in the library) | `201` the new image |
| `PUT` | `/products/{id}/images/order` | Admin | `{ "imageIds": [7, 4] }` — every image id, new order | `200` the gallery in its new order |
| `DELETE` | `/products/{id}/images/{imageId}` | Admin | — | `204` |

#### Errors (standard `ApiError` body)
| Status | `code` | When |
| :--- | :--- | :--- |
| `400` | `VALIDATION_FAILED` | No file / bad filename, or `imageIds` doesn't list every image exactly once |
| `404` | `NOT_FOUND` | Product, library file or gallery image doesn't exist (or belongs to another product) |
| `422` | `BUSINESS_RULE_VIOLATION` | Gallery already has 10 images, or the image is already the cover / in the gallery |

---

## 4. Shipping & Governorates

When placing an order, the system calculates the delivery charge automatically based on the customer's registered `governorate`. The frontend can dynamically fetch governorates and their shipping fees using the public endpoints below (also aliased under `/shipping/governorates/**`).

> **Source of truth:** prices come from the **Shipping Zones** the admin edits in the ERP (`ShippingZones` table). Nothing is hardcoded: a price changed in the ERP is used by the next request — the store's price list, store checkout charges and sign-up validation all read it. Inactive zones are hidden and can't be ordered to.
>
> **Clients must not cache prices for long.** Reload `GET /governorates` when the cart or checkout opens; the store app does this.

### 4.1 Get All Governorates & Prices

Returns an ordered list of all supported Egyptian governorates and their shipping fees. Ideal for populating dropdowns during customer registration and checkout.

- **Endpoint**: `GET /governorates` (or `GET /shipping/governorates`)
- **Auth Required**: No

#### Response `200 OK`
```json
[
  {
    "governorate": "القاهرة",
    "price": 80.0
  },
  {
    "governorate": "الجيزة",
    "price": 80.0
  },
  {
    "governorate": "الإسكندرية",
    "price": 90.0
  }
]
```

---

### 4.2 Get Governorates as Key-Value Map

Returns all governorates and their delivery fees as a JSON dictionary (`{ "<governorate>": <price> }`) for fast O(1) lookup on the frontend.

- **Endpoint**: `GET /governorates/map` (or `GET /shipping/governorates/map`)
- **Auth Required**: No

#### Response `200 OK`
```json
{
  "القاهرة": 80.0,
  "الجيزة": 80.0,
  "القليوبية": 100.0,
  "الإسكندرية": 90.0
}
```

---

### 4.3 Get Delivery Price for a Specific Governorate

Normalizes the Arabic governorate name (handling common spelling variations like `ه`/`ة` or `أ`/`ا`) and returns its exact shipping fee.

- **Endpoint**: `GET /governorates/price?governorate={name}` (or `GET /shipping/governorates/price?governorate={name}`)
- **Auth Required**: No

#### Example Request
```http
GET /governorates/price?governorate=القاهره
```

#### Response `200 OK`
```json
{
  "governorate": "القاهرة",
  "price": 80.0
}
```

#### Response `400 Bad Request` (no active zone for this governorate)
Standard `ApiError` body:
```json
{
  "status": 400,
  "code": "VALIDATION_FAILED",
  "message": "We don't deliver to 'UnknownCity' yet. Choose another governorate.",
  "details": [{ "field": "governorate", "message": "We don't deliver to 'UnknownCity' yet. Choose another governorate." }]
}
```

> ⚠️ **Changed:** this used to be `{ "error": "..." }`.

---

### 4.4 Managing Shipping Zones (ERP admin)

| Method | Endpoint | Auth | Body / Params | Notes |
| :--- | :--- | :--- | :--- | :--- |
| `GET` | `/shipping` | Admin | — | All zones, active and inactive: `{id, cityName, shippingFee, isActive, …}` |
| `POST` | `/shipping` | Admin | `{ "cityName": "مطروح", "shippingFee": 130 }` | `409 CONFLICT` if the governorate already has a zone (spelling variants count as the same) |
| `PUT` | `/shipping/{id}?shippingFee=95` | Admin | — | Changes the price; applies to the store immediately |
| `DELETE` | `/shipping/{id}` | Admin | — | Customers in that governorate can no longer check out |

Prices must be `0` or more (`400 VALIDATION_FAILED` otherwise).

The initial zones are created by `schema-sync-shipping-zones.sql`, which only fills in governorates that don't have a zone yet; prices edited in the ERP are never overwritten.

---

## 5. E-Commerce Order Placement

### 5.1 Placing an Order

- **Endpoint**: `POST /orders/placeOrder`
- **Content-Type**: `multipart/form-data`
- **Auth Required**: **Yes** (`Authorization: Bearer <customer_token>`)

#### `order` JSON Payload Schema:
```json
{
  "bankAccountId": 1,
  "paymentMethod": "Instapay",
  "orderSource": "Website",
  "notes": "Please deliver after 5 PM. Wood finish color: Dark Walnut.",
  "downPayment": 500.0,
  "items": [
    {
      "productId": 15,
      "variantId": 32,
      "quantity": 1
    },
    {
      "productId": 22,
      "quantity": 2
    }
  ]
}
```

> **`variantId`** (new): required when the product has variants (`hasVariants: true`), and must be omitted/`null` for products without variants. The line is charged at the variant's price, and stock is taken from that variant.
>
> | Status | `code` | Message (example) |
> | :--- | :--- | :--- |
> | `400` | `VALIDATION_FAILED` | `Choose a size/color for 'Linen Shirt'.` |
> | `400` | `VALIDATION_FAILED` | `The chosen size/color doesn't belong to 'Linen Shirt'.` |
> | `409` | `INSUFFICIENT_STOCK` | `Not enough stock for 'Linen Shirt (XL / Black)' (available: 1, requested: 2).` |
> | `422` | `BUSINESS_RULE_VIOLATION` | `'Linen Shirt (XL / Black)' is no longer available.` |

#### Response `200 OK` (`OrderResponse`):
```json
{
  "orderId": 1042,
  "orderDate": "2026-09-30",
  "status": "Pending",
  "orderSource": "Website",
  "discount": 500.0,
  "delivery": 80.0,
  "totalPrice": 4080.0,
  "downPayment": 500.0,
  "paymentMethod": "Instapay",
  "notes": "Deliver after 5 PM",
  "customer": {
    "customerId": 7,
    "fullName": "Ahmed Mohamed",
    "governorate": "القاهرة",
    "phone": "01012345678",
    "secondaryPhone": "01187654321",
    "city": "Nasr City",
    "address": "12 Abbas El Akkad St, Apt 4"
  },
  "orderItems": [
    {
      "productId": 15,
      "productName": "Linen Shirt",
      "productNameAr": "قميص كتان",
      "variantId": 32,
      "size": "XL",
      "color": "Black",
      "quantity": 1,
      "unitPrice": 500.0
    }
  ]
}
```

> `productNameAr`, `variantId`, `size` and `color` are new on every order line (orders, order lists, invoices). They are `null` for products without variants.

---

### 5.2 Fetching Customer Order History

Allows the authenticated customer to view all their past and current orders.

- **Endpoint**: `GET /orders/customer`
- **Auth Required**: **Yes** (`Authorization: Bearer <customer_token>`)

#### Response `200 OK`
Returns an array of `OrderResponse` objects for the authenticated customer sorted by order ID descending.

---

## 6. Customer Feedbacks, Reviews & Comments

This feature allows **every user** (authenticated customers or guest visitors) to submit feedbacks, star ratings, suggestions, product reviews, or complaints. Customers can also comment on feedback threads, view their own feedback history, and view public testimonials and product reviews.

### Supported Feedback Types:
- `GENERAL` — General store / brand feedback (Default)
- `SERVICE` — Customer service, maintenance, or repair service feedback
- `PRODUCT` — Review for a specific catalog product (accepts `productId`)
- `ORDER` — Feedback on a specific order (accepts `orderId`)
- `SUGGESTION` — Ideas, improvements, or feature suggestions
- `COMPLAINT` — Issue or complaint report

---

### 6.1 Submitting Feedback (`POST /feedbacks`)

Enables any user to submit feedback.
- **Logged-in Customer**: Pass `Authorization: Bearer <token>`. The backend automatically associates the feedback with the customer's ID and contact details.
- **Guest Visitor**: No token needed. Provide `customerName` and `customerEmail` (or `customerPhone`) in the JSON payload.

- **Endpoint**: `POST /feedbacks`
- **Content-Type**: `application/json`
- **Auth Required**: **Optional** (Public / Bearer token)

#### Request Body Schema:
```json
{
  "feedbackType": "GENERAL",
  "rating": 5,
  "subject": "Exceptional craftsmanship and fast delivery!",
  "comment": "Ordered custom furniture and was blown away by the finishing quality and friendly service.",
  "productId": 15,
  "orderId": 1042,
  "customerName": "Ahmed Mohamed",
  "customerEmail": "ahmed.mohamed@example.com",
  "customerPhone": "01012345678"
}
```

> **Validation Rules**:
> - `rating`: **Required**, Integer between `1` and `5`.
> - `comment`: **Required**, String (cannot be blank).
> - `customerName`: **Required for guest users** (optional if logged in).
> - `productId`: Optional (Long).
> - `orderId`: Optional (Long).

#### Response `201 Created` (`FeedbackResponse`):
```json
{
  "id": 1,
  "customerId": 7,
  "customerName": "Ahmed Mohamed",
  "customerEmail": "ahmed.mohamed@example.com",
  "customerPhone": "01012345678",
  "productId": 15,
  "productName": "Oak Dining Table",
  "orderId": 1042,
  "feedbackType": "GENERAL",
  "subject": "Exceptional craftsmanship and fast delivery!",
  "comment": "Ordered custom furniture and was blown away by the finishing quality and friendly service.",
  "rating": 5,
  "status": "PENDING",
  "isPublic": false,
  "isFeatured": false,
  "adminReply": null,
  "repliedBy": null,
  "repliedAt": null,
  "createdAt": "2026-10-15T23:30:00",
  "updatedAt": "2026-10-15T23:30:00",
  "comments": []
}
```

---

### 6.2 Fetching Public Approved Feedbacks & Testimonials (`GET /feedbacks/public`)

Fetches approved public customer feedbacks and testimonials to display on the website homepage, reviews carousel, or testimonials section.

- **Endpoint**: `GET /feedbacks/public`
- **Auth Required**: No

#### Query Parameters:
| Parameter | Type | Required | Description |
| :--- | :--- | :--- | :--- |
| `type` | String | No | Filter by type: `GENERAL`, `SERVICE`, `PRODUCT`, `ORDER`, `SUGGESTION`, `COMPLAINT` |
| `featuredOnly` | Boolean | No | Pass `true` to retrieve only featured testimonials for hero/homepage carousels. Default: `false`. |

#### Example Request:
```http
GET /feedbacks/public?featuredOnly=true
```

#### Response `200 OK`:
```json
[
  {
    "id": 1,
    "customerId": 7,
    "customerName": "Ahmed Mohamed",
    "customerEmail": "ahmed.mohamed@example.com",
    "customerPhone": "01012345678",
    "productId": null,
    "productName": null,
    "orderId": null,
    "feedbackType": "GENERAL",
    "subject": "Superb quality!",
    "comment": "The best workshop in Cairo. Incredible attention to detail!",
    "rating": 5,
    "status": "APPROVED",
    "isPublic": true,
    "isFeatured": true,
    "adminReply": "Thank you Ahmed, always a pleasure!",
    "repliedBy": "admin",
    "repliedAt": "2026-10-16T10:00:00",
    "createdAt": "2026-10-15T20:00:00",
    "updatedAt": "2026-10-16T10:00:00",
    "comments": [
      {
        "commentId": 1,
        "feedbackId": 1,
        "customerId": null,
        "userId": 1,
        "authorName": "admin",
        "authorRole": "ADMIN",
        "commentText": "Thank you Ahmed, always a pleasure!",
        "createdAt": "2026-10-16T10:00:00"
      }
    ]
  }
]
```

---

### 6.3 Product Reviews & Rating Summary (`GET /feedbacks/product/{productId}`)

Fetches all approved reviews for a specific product along with calculated rating metrics (average rating, total count, star rating breakdown) — ideal for product details pages.

- **Endpoint**: `GET /feedbacks/product/{productId}`
- **Auth Required**: No

#### Example Request:
```http
GET /feedbacks/product/15
```

#### Response `200 OK` (`ProductFeedbackSummaryDto`):
```json
{
  "productId": 15,
  "productName": "Oak Dining Table",
  "averageRating": 4.8,
  "totalReviews": 24,
  "ratingDistribution": {
    "5": 19,
    "4": 4,
    "3": 1,
    "2": 0,
    "1": 0
  },
  "reviews": [
    {
      "id": 5,
      "customerId": 7,
      "customerName": "Ahmed Mohamed",
      "customerEmail": "ahmed.mohamed@example.com",
      "customerPhone": "01012345678",
      "productId": 15,
      "productName": "Oak Dining Table",
      "orderId": 1042,
      "feedbackType": "PRODUCT",
      "subject": "Solid construction",
      "comment": "Table is rock solid and color matched photos perfectly.",
      "rating": 5,
      "status": "APPROVED",
      "isPublic": true,
      "isFeatured": false,
      "adminReply": null,
      "repliedBy": null,
      "repliedAt": null,
      "createdAt": "2026-10-14T14:22:00",
      "updatedAt": "2026-10-14T14:22:00",
      "comments": []
    }
  ]
}
```

---

### 6.4 Fetching Customer's Own Feedbacks (`GET /feedbacks/customer`)

Allows the authenticated customer to view all feedbacks they have submitted and check their approval status or official admin replies.

- **Endpoint**: `GET /feedbacks/customer`
- **Auth Required**: **Yes** (`Authorization: Bearer <customer_token>`)

#### Response `200 OK`:
Returns an array of `FeedbackResponse` objects belonging to the current customer.

---

### 6.5 Getting Single Feedback by ID (`GET /feedbacks/{id}`)

Fetches complete feedback information including the thread of comments and replies.

- **Endpoint**: `GET /feedbacks/{id}`
- **Auth Required**: No

#### Response `200 OK`:
Returns a single `FeedbackResponse` object.

---

### 6.6 Adding a Comment / Reply to a Feedback Thread (`POST /feedbacks/{id}/comments`)

Allows customers, admins, or guest users to reply or add comments on an existing feedback conversation.

- **Endpoint**: `POST /feedbacks/{id}/comments`
- **Content-Type**: `application/json`
- **Auth Required**: Optional (If logged in, author name and role are populated automatically)

#### Request Body:
```json
{
  "commentText": "Thank you for the prompt resolution! Issue has been resolved.",
  "authorName": "Ahmed Mohamed"
}
```

#### Response `201 Created` (`FeedbackCommentDto`):
```json
{
  "commentId": 3,
  "feedbackId": 1,
  "customerId": 7,
  "userId": null,
  "authorName": "Ahmed Mohamed",
  "authorRole": "CUSTOMER",
  "commentText": "Thank you for the prompt resolution! Issue has been resolved.",
  "createdAt": "2026-10-15T23:45:00"
}
```

---

### 6.7 Admin ERP Feedback Moderation Endpoints

These endpoints are used by the workshop administrative dashboard to moderate, reply to, and organize customer feedback.

| Method | Endpoint | Description | Auth Required |
| :--- | :--- | :--- | :--- |
| `GET` | `/feedbacks` | Filter & search all feedbacks (`status`, `type`, `rating`, `productId`, `startDate`, `endDate`, `search`) | Admin Bearer Token |
| `PUT` | `/feedbacks/{id}/status` | Update moderation status (`status`: `PENDING` / `APPROVED` / `REJECTED` / `RESOLVED`, `isPublic`: boolean, `isFeatured`: boolean) | Admin Bearer Token |
| `POST` | `/feedbacks/{id}/reply` | Submit official admin reply to customer feedback | Admin Bearer Token |
| `DELETE` | `/feedbacks/{id}` | Delete feedback permanently | Admin Bearer Token |

#### Admin Status Update Body Example:
```json
{
  "status": "APPROVED",
  "isPublic": true,
  "isFeatured": true
}
```

#### Admin Reply Body Example:
```json
{
  "reply": "Thank you for your valuable feedback! We have credited your account with a loyalty discount."
}
```

---

## 7. Frontend Integration Workflow & Code Examples

### 7.1 Axios Instance & Interceptor Setup (TypeScript / JavaScript)

```typescript
import axios from 'axios';

const api = axios.create({
  baseURL: process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080',
});

// Request Interceptor: Attach JWT Token
api.interceptors.request.use((config) => {
  const token = localStorage.getItem('customer_token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// Response Interceptor: Handle Authentication Expiry & Pending Status
api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      // Clear token and redirect to login
      localStorage.removeItem('customer_token');
      window.location.href = '/login';
    }
    return Promise.reject(error);
  }
);

export default api;
```

---

### 7.2 Feedback API Helper (TypeScript)

```typescript
import api from './api';

export interface CreateFeedbackPayload {
  feedbackType?: 'GENERAL' | 'SERVICE' | 'PRODUCT' | 'ORDER' | 'SUGGESTION' | 'COMPLAINT';
  rating: number; // 1 - 5
  subject?: string;
  comment: string;
  productId?: number;
  orderId?: number;
  customerName?: string;  // Required for guest
  customerEmail?: string; // Optional for guest
  customerPhone?: string; // Optional for guest
}

export interface FeedbackCommentDto {
  commentId: number;
  feedbackId: number;
  customerId?: number;
  userId?: number;
  authorName: string;
  authorRole: 'CUSTOMER' | 'ADMIN';
  commentText: string;
  createdAt: string;
}

export interface FeedbackResponse {
  id: number;
  customerId?: number;
  customerName: string;
  customerEmail?: string;
  customerPhone?: string;
  productId?: number;
  productName?: string;
  orderId?: number;
  feedbackType: string;
  subject?: string;
  comment: string;
  rating: number;
  status: 'PENDING' | 'APPROVED' | 'REJECTED' | 'RESOLVED';
  isPublic: boolean;
  isFeatured: boolean;
  adminReply?: string;
  repliedBy?: string;
  repliedAt?: string;
  createdAt: string;
  updatedAt: string;
  comments: FeedbackCommentDto[];
}

export interface ProductFeedbackSummary {
  productId: number;
  productName: string;
  averageRating: number;
  totalReviews: number;
  ratingDistribution: Record<number, number>;
  reviews: FeedbackResponse[];
}

/**
 * Submit feedback (logged-in customer or guest)
 */
export async function submitFeedback(payload: CreateFeedbackPayload): Promise<FeedbackResponse> {
  const response = await api.post<FeedbackResponse>('/feedbacks', payload);
  return response.data;
}

/**
 * Fetch approved public feedbacks / testimonials
 */
export async function getPublicFeedbacks(params?: { type?: string; featuredOnly?: boolean }): Promise<FeedbackResponse[]> {
  const response = await api.get<FeedbackResponse[]>('/feedbacks/public', { params });
  return response.data;
}

/**
 * Fetch reviews and ratings summary for a product
 */
export async function getProductReviews(productId: number): Promise<ProductFeedbackSummary> {
  const response = await api.get<ProductFeedbackSummary>(`/feedbacks/product/${productId}`);
  return response.data;
}

/**
 * Fetch customer's own feedback history
 */
export async function getCustomerFeedbacks(): Promise<FeedbackResponse[]> {
  const response = await api.get<FeedbackResponse[]>('/feedbacks/customer');
  return response.data;
}

/**
 * Add a comment or reply to a feedback thread
 */
export async function addFeedbackComment(feedbackId: number, commentText: string, authorName?: string): Promise<FeedbackCommentDto> {
  const response = await api.post<FeedbackCommentDto>(`/feedbacks/${feedbackId}/comments`, {
    commentText,
    authorName,
  });
  return response.data;
}
```

---

### 7.3 Full Checkout & Order Placement Helper

```typescript
import api from './api';

export interface OrderItemPayload {
  productId: number;
  quantity: number;
}

export interface PlaceOrderPayload {
  bankAccountId?: number;
  paymentMethod: string;
  orderSource?: string;
  notes?: string;
  downPayment?: number;
  items: OrderItemPayload[];
  images?: File[];
}

export async function placeCustomerOrder(payload: PlaceOrderPayload) {
  const formData = new FormData();

  const orderData = {
    bankAccountId: payload.bankAccountId || 1,
    paymentMethod: payload.paymentMethod || 'Cash on Delivery',
    orderSource: payload.orderSource || 'Website',
    notes: payload.notes || '',
    downPayment: payload.downPayment || 0,
    items: payload.items,
  };

  // Important: Must serialize the JSON blob with type application/json
  formData.append(
    'order',
    new Blob([JSON.stringify(orderData)], { type: 'application/json' })
  );

  // Append optional images/attachments
  if (payload.images && payload.images.length > 0) {
    payload.images.forEach((file) => {
      formData.append('images', file);
    });
  }

  const response = await api.post('/orders/placeOrder', formData, {
    headers: {
      'Content-Type': 'multipart/form-data',
    },
  });

  return response.data;
}
```

---

## 8. Common Error Responses & Status Codes

| Status Code | Reason | Resolution / Action |
| :--- | :--- | :--- |
| `400 Bad Request` | Invalid payload, invalid rating, empty comment, invalid governorate name, or expired/invalid OTP | Inspect error message string and show user-friendly form error. |
| `401 Unauthorized` | Invalid email/password, or missing/invalid JWT token | Prompt user to log in again. |
| `403 Forbidden` | Customer account is `Pending` OTP verification | Redirect customer to `/verify-otp` view. |
| `404 Not Found` | Feedback or Product not found | Display "Item not found" or handle gracefully. |
| `409 Conflict` / `500` | Insufficient stock for one or more requested items | Display "Product out of stock" message to the customer. |
