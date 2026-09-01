# Human Resource Management System (HRMS)

<div align="center">

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Status](https://img.shields.io/badge/Status-Active-success.svg)]()
[![Contributions Welcome](https://img.shields.io/badge/Contributions-Welcome-brightgreen.svg)]()

A comprehensive Human Resource Management System designed to streamline employee tracking, attendance, department organization, and day-to-day administrative HR workflows.

</div>


A modern, production-oriented **Human Resource Management System (HRMS)** desktop application built with **Java Swing**, **MySQL**, and **Maven**.

The system is designed to help organizations manage employees, attendance, leave, payroll-related information, performance, permissions, notifications, and other HR operations through a centralized desktop application.

> **Project Status:** 🚧 Active Development

---



## 📌 Overview

**HRMS** is a desktop-based Human Resource Management System developed using Java and Swing.

The project focuses on building a maintainable, modular, and professional HR application with:

* Role-based access control
* Permission-based navigation
* Employee management
* Attendance management
* Leave management
* Payroll-related functionality
* Performance management
* Notifications
* Dashboard and reporting
* PDF and Excel export
* QR-code functionality
* Modern Light/Dark UI support
* MySQL database integration

The application uses a layered architecture to separate UI, controllers, services, repositories, models, security, and infrastructure responsibilities.

---

## ✨ Features

### 🔐 Authentication & Security

* User authentication
* Session management
* Role-based access control
* Permission-based authorization
* BCrypt password hashing
* Protected application modules
* Logout and session handling

### 👥 Employee Management

* Employee records
* Employee profiles
* Department management
* Position/job information
* Employee status management
* Employee search and filtering

### 🕒 Attendance Management

* Employee attendance records
* Attendance status tracking
* Check-in / check-out related functionality
* Attendance reporting
* Attendance-related HR workflows

### 📅 Leave Management

* Leave type management
* Leave application
* Leave assignment
* Leave approval workflow
* Leave balance tracking
* Leave history

### 📋 Performance Management

* KPI-related information
* Performance records
* Performance evaluation workflows
* Performance tracking

### 💰 Payroll & Compensation

* Salary-related information
* Payroll-related records
* Compensation management
* Payroll reporting/export functionality

### 🔔 Notifications

* Application notifications
* Notification management
* User-specific notification handling

### 📊 Dashboard

The HRMS dashboard provides a centralized overview of important HR information.

Possible dashboard information includes:

* Employee statistics
* Attendance statistics
* Leave statistics
* Performance information
* HR-related summaries
* Charts and visual reports

Charts are implemented using **JFreeChart**.

### 📄 Reports & Export

The application supports document/report generation using:

* **Apache PDFBox** for PDF generation
* **Apache POI** for Microsoft Excel documents

### 🔳 QR Code

QR-code functionality is implemented using **ZXing**.

This can be used for HR-related identification and scanning workflows.

### 🎨 Modern UI

The application uses:

* **FlatLaf** for the modern Swing look and feel
* **MigLayout** for flexible component layout
* SVG/icon support
* Light/Dark theme support
* Reusable Swing components

---

## 🛠️ Technology Stack

| Technology    | Purpose                       |
| ------------- | ----------------------------- |
| Java 25       | Application development       |
| Java Swing    | Desktop UI                    |
| Maven         | Dependency & build management |
| MySQL         | Relational database           |
| JDBC          | Database communication        |
| HikariCP      | Database connection pooling   |
| Flyway        | Database migration            |
| FlatLaf       | Modern Swing Look & Feel      |
| MigLayout     | UI layout management          |
| JFreeChart    | Charts & visualization        |
| Apache PDFBox | PDF generation                |
| Apache POI    | Excel generation              |
| ZXing         | QR code processing            |
| BCrypt        | Password hashing              |
| SLF4J         | Logging API                   |
| Logback       | Logging implementation        |
| JUnit 5       | Testing                       |
| AssertJ       | Test assertions               |

The current Maven configuration targets **Java 25** and packages the application as a JAR with Maven Shade, with `com.ams.hrms.Main` configured as the application entry point.

---

## 🏗️ Architecture

The application follows a layered architecture designed to keep responsibilities separated.

```text
┌─────────────────────────────────────────────┐
│                  UI Layer                   │
│        Java Swing / FlatLaf / MigLayout     │
└──────────────────────┬──────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────┐
│              Controller Layer               │
│       UI Events / User Interaction          │
└──────────────────────┬──────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────┐
│               Service Layer                 │
│        Business Logic / Validation          │
└──────────────────────┬──────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────┐
│             Repository / DAO Layer          │
│           Database Operations               │
└──────────────────────┬──────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────┐
│                  MySQL                     │
│             HRMS Database                  │
└─────────────────────────────────────────────┘
```

### Main responsibilities

**UI**

Responsible for displaying information and collecting user input.

**Controller**

Coordinates UI actions and communicates with application services.

**Service**

Contains business rules and application logic.

**Repository / DAO**

Handles database access and persistence operations.

**Security**

Handles authentication, authorization, sessions, roles, and permissions.

---

## 📂 Project Structure

A simplified structure of the project is:

```text
HRMS/
│
├── src/
│   ├── main/
│   │   └── java/
│   │       └── com/
│   │           └── ams/
│   │               └── hrms/
│   │                   │
│   │                   ├── config/
│   │                   ├── component/
│   │                   ├── controller/
│   │                   ├── db/
│   │                   ├── event/
│   │                   ├── model/
│   │                   ├── repository/
│   │                   ├── security/
│   │                   ├── service/
│   │                   ├── ui/
│   │                   ├── util/
│   │                   │
│   │                   └── Main.java
│   │
│   └── test/
│       └── java/
│           └── com/
│               └── ams/
│                   └── hrms/
│
├── pom.xml
├── .gitignore
└── README.md
```

---

## 💻 Requirements

Before running the application, install:

### Required

* **JDK 25**
* **Maven 3.9+**
* **MySQL 8.x**
* Git

Check your Java installation:

```bash
java -version
```

Check Maven:

```bash
mvn -version
```

---

## 🚀 Getting Started

### 1. Clone the Repository

```bash
git clone https://github.com/AungMratSoe-coder/HRMS.git
```

Move into the project directory:

```bash
cd HRMS
```

---

### 2. Configure MySQL

Make sure MySQL Server is running.

Create the HRMS database:

```sql
CREATE DATABASE hrms;
```

If your project configuration uses a different database name, username, or password, update the corresponding database configuration in the application.

Example:

```text
Host: localhost
Port: 3306
Database: hrms
Username: root
Password: your_password
```

> **Security:** Never commit real database passwords, API keys, or other secrets to GitHub.

---

### 3. Build the Project

Run:

```bash
mvn clean package
```

Maven will:

1. Clean previous build files
2. Compile the project
3. Run tests
4. Package the application
5. Create the executable JAR

The project uses Maven Shade to create a packaged JAR containing its runtime dependencies.

---

### 4. Run the Application

The main application class is:

```text
com.ams.hrms.Main
```

You can run it from your IDE or execute the packaged JAR:

```bash
java -jar target/hr-management-system-1.0.0.jar
```

---

## 🧪 Running Tests

Run the test suite with:

```bash
mvn test
```

The project uses:

* JUnit Jupiter
* AssertJ

for automated testing.

---

## 🔑 Security

Security is an important part of the HRMS architecture.

The application includes concepts such as:

```text
Authentication
      │
      ▼
Session
      │
      ▼
User
      │
      ▼
Role
      │
      ▼
Permissions
      │
      ▼
Authorized Modules
```

Passwords should never be stored as plain text.

The project uses **BCrypt** for password hashing.

---

## 🧩 Navigation & Permissions

The application uses permission-based navigation.

A typical flow is:

```text
User Login
    │
    ▼
Authentication
    │
    ▼
Create Session
    │
    ▼
Load User Permissions
    │
    ▼
Build Navigation Menu
    │
    ▼
Display Authorized Modules
```

This allows different users to access different HRMS modules according to their assigned permissions.

---

## 🖥️ Main Application Modules

The HRMS application is organized around major HR functions.

```text
Dashboard
│
├── Employee Management
├── Attendance
├── Leave Management
├── Payroll
├── Performance
├── Notifications
├── Reports
├── User Management
├── Role & Permission Management
└── System Settings
```

The exact modules available to a user depend on their permissions.

---

## 📊 Reporting

HRMS provides reporting capabilities for HR-related information.

Supported document technologies include:

### PDF

Powered by:

```text
Apache PDFBox
```

### Excel

Powered by:

```text
Apache POI
```

### Charts

Powered by:

```text
JFreeChart
```

---

## 🔳 QR Code Support

QR-code functionality is implemented with:

```text
ZXing Core
ZXing JavaSE
```

This provides the foundation for QR-code generation and scanning workflows.

---

## 🎨 UI & Design

The UI is built entirely with Java Swing and enhanced with modern libraries.

### FlatLaf

Provides the modern application theme and Look & Feel.

### MigLayout

Used to create flexible and maintainable Swing layouts.

Example:

```java
new MigLayout(
    "fill",
    "[grow]",
    "[]10[grow]"
);
```

This allows the UI to adapt more easily than fixed-position Swing layouts.

---

## 🗃️ Database

The application uses:

```text
Java
  │
  ▼
JDBC
  │
  ▼
HikariCP
  │
  ▼
MySQL
```

HikariCP provides database connection pooling, while Flyway is included for database migration/version management.

---

## 🔄 Database Migration

Flyway is used for database schema version management.

The general migration concept is:

```text
Migration V1
     │
     ▼
Migration V2
     │
     ▼
Migration V3
     │
     ▼
Current Database Schema
```

This makes database changes easier to track and reproduce between development environments.

---

## 📦 Build & Packaging

The project uses Maven for dependency management and packaging.

Main Maven commands:

```bash
# Clean
mvn clean

# Compile
mvn compile

# Run tests
mvn test

# Build application
mvn package

# Clean and build
mvn clean package
```

The Maven project currently uses:

```text
groupId:    com.ams
artifactId: hr-management-system
version:    1.0.0
```

and targets Java 25.

---

## 🧑‍💻 Development

### Recommended IDEs

You can develop the project using:

* IntelliJ IDEA
* Apache NetBeans
* Eclipse
* VS Code with Java extensions

For Swing development, IntelliJ IDEA or NetBeans can be convenient depending on your preferred workflow.

---

## 🤝 Contributing

Contributions are welcome.

### Recommended workflow

```text
main
 │
 └── develop
       │
       ├── feature/employee-management
       ├── feature/attendance
       ├── feature/leave
       └── feature/payroll
```

For a new feature:

```bash
git checkout develop
git pull

git checkout -b feature/my-feature
```

After completing the feature:

```bash
git add .
git commit -m "Add my feature"
git push origin feature/my-feature
```

Then create a Pull Request targeting `develop`.

---

## 🐛 Issues & Feature Requests

If you find a bug or have an improvement idea, please create an issue in the GitHub repository.

Useful issue information includes:

* Description of the problem
* Steps to reproduce
* Expected behavior
* Actual behavior
* Java version
* MySQL version
* Operating system
* Screenshots or logs

---

## 🔮 Future Improvements

Potential future improvements include:

* [ ] Advanced employee self-service portal
* [ ] Advanced payroll calculation
* [ ] Attendance device integration
* [ ] Biometric attendance integration
* [ ] Email notifications
* [ ] More advanced HR analytics
* [ ] Advanced PDF reports
* [ ] Automated backups
* [ ] More comprehensive test coverage
* [ ] CI/CD pipeline
* [ ] Application installer
* [ ] Multi-language support
* [ ] Cloud/database deployment options

---

## 📸 Screenshots

Add application screenshots here as the UI becomes finalized.

Example:

```markdown
## Login

![Login Screen](docs/screenshots/login.png)

## Dashboard

![Dashboard](docs/screenshots/dashboard.png)

## Employee Management

![Employee Management](docs/screenshots/employees.png)
```

Recommended directory:

```text
docs/
└── screenshots/
    ├── login.png
    ├── dashboard.png
    ├── employees.png
    ├── attendance.png
    └── leave.png
```

---

## 📚 Learning Objectives

This project is also intended to demonstrate practical software engineering concepts, including:

* Object-Oriented Programming
* Layered Architecture
* MVC concepts
* Repository Pattern
* Service Layer
* Dependency Management
* Database Access with JDBC
* Connection Pooling
* Authentication & Authorization
* Role-Based Access Control
* Event-Driven UI
* Design Patterns
* Exception Handling
* Logging
* Unit Testing
* Database Migration
* Desktop Application Packaging

---

## 👨‍💻 Author

**Aung Mrat Soe**

GitHub:

[AungMratSoe-coder](https://github.com/AungMratSoe-coder?utm_source=chatgpt.com)

Project:

[HRMS Repository](https://github.com/AungMratSoe-coder/HRMS?utm_source=chatgpt.com)

---

## ⭐ Support

If you find this project useful or interesting, consider giving the repository a ⭐ on GitHub.

---

## 📄 License

Add your project's license here.

If this project is intended to be open source, an appropriate license such as MIT can be added to the repository as `LICENSE`.

---

**Built with Java ☕, Swing 🖥️, MySQL 🗄️ and Maven 📦**
