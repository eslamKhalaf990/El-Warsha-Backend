# Technical Skills and Expertise Demonstrated in the ERP Project

This document outlines the key technical skills, technologies, and engineering practices utilized in this ERP backend application.

### **Core Backend Development**

*   **Java 21:** Leveraged modern Java features for building a high-performance, robust backend.
*   **Spring Boot 3:** Utilized the Spring Boot framework to create a stand-alone, production-grade application with minimal configuration.
*   **Spring Framework:**
    *   **Spring MVC:** Built a comprehensive RESTful API with clear and structured controllers (`@RestController`, `@RequestMapping`).
    *   **Spring Data JPA:** Implemented a clean and efficient data access layer, abstracting away boilerplate JDBC code.
    *   **Dependency Injection:** Used `@Autowired` extensively to manage application components and promote loose coupling.

### **API Design & Architecture**

*   **RESTful API Design:** Engineered a full-fledged REST API for all ERP modules, adhering to standard HTTP conventions for CRUD operations.
*   **Service-Oriented Architecture (SOA):** Designed the application with a clear separation of concerns, where controllers handle API requests, services contain business logic, and repositories manage data access.
*   **Data Transfer Objects (DTOs):** Employed DTOs to create a stable API contract, decouple the API from the internal data model, and prevent exposing sensitive entity information.

### **Database & Persistence**

*   **Microsoft SQL Server:** Used as the primary relational database for the application.
*   **JPA (Java Persistence API):**
    *   **Entity Modeling:** Designed a normalized and efficient database schema using JPA annotations (`@Entity`, `@Table`).
    *   **Relationships:** Modeled complex data relationships, including `@ManyToOne`, `@OneToOne`, and `@OneToMany`.
    *   **Lifecycle Management:** Used `cascade` and `orphanRemoval` to ensure data integrity across related entities.
*   **Transactional Management:**
    *   **ACID Compliance:** Guaranteed data consistency using `@Transactional` annotations to ensure that business operations are atomic.
    *   **Concurrency Control:** Implemented pessimistic locking to prevent race conditions and ensure data integrity during concurrent updates (e.g., stock management).

### **Security**

*   **Spring Security:** Implemented a robust security layer to protect API endpoints.
*   **JWT (JSON Web Tokens):** Used for stateless authentication and authorization, ensuring that the API remains scalable and secure.

### **Third-Party Integrations & Libraries**

*   **Google Drive API:** Integrated for seamless cloud-based file and document management.
*   **JavaMail:** Used for sending automated email notifications (e.g., order confirmations, status updates).
*   **OpenPDF:** Leveraged for dynamic PDF generation (e.g., invoices, reports).
*   **Lombok:** Utilized to reduce boilerplate code in model and entity classes.

### **Software Engineering Practices**

*   **Modular Design:** The codebase is organized into logical modules (controllers, services, repositories, etc.), making it easy to navigate, maintain, and scale.
*   **Exception Handling:** Implemented a global exception handler to gracefully manage application-wide errors.
*   **Code Quality:** The code demonstrates a strong understanding of clean code principles, with clear naming conventions and a logical structure.
