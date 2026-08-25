# HR Management System — Role-Based User Manual

**Version:** 1.0.0
**Application Type:** Desktop Application (Java Swing + MySQL)
**Developer:** AMS
**Companion Document:** `USER_MANUAL_MM.md` (full feature walkthrough)

This manual explains what **each type of user** can do in the system, organized by role. Read the chapter that matches your role.

---

## Table of Contents

1. [Getting Started (All Users)](#1-getting-started-all-users)
2. [Roles at a Glance](#2-roles-at-a-glance)
3. [Module Access Matrix](#3-module-access-matrix)
4. [Super Administrator](#4-super-administrator)
5. [HR Manager](#5-hr-manager)
6. [HR Officer](#6-hr-officer)
7. [Department Manager](#7-department-manager)
8. [Finance](#8-finance)
9. [Employee (Self-Service)](#9-employee-self-service)
10. [Shared Features](#10-shared-features)
11. [Quick Troubleshooting by Role](#11-quick-troubleshooting-by-role)

---

## 1. Getting Started (All Users)

### 1.1 Launching the Application

```
java -jar target/hr-management-system-1.0.0.jar
```

### 1.2 Signing In

| Field | Value |
|---|---|
| Username | your assigned username |
| Password | your password |

- Default development administrator: `admin` / `Admin@123` (**change after first login**).
- Use 👁️ icon to show/hide password; tick **Remember username** to auto-fill next time.
- Press **Enter** or click **Sign In**.

### 1.3 Security Rules

- **5 failed attempts within 30 seconds** locks the account temporarily ("Too many failed attempts…"). Wait and retry.
- If an administrator resets your password, you will be prompted to **change it immediately** after logging in (forced password-change dialog).
- Your sidebar only shows modules permitted for your role. Opening a forbidden module shows an **"Access denied"** panel.

---

## 2. Roles at a Glance

| Role | Who It Is For | Main Responsibility |
|---|---|---|
| **Super Administrator** | IT / system owners | Everything, including user accounts, settings and audit log |
| **HR Manager** | Head of HR | Full employee lifecycle oversight and final HR approvals |
| **HR Officer** | HR staff | Day-to-day HR operations: records, attendance, leave, recruitment |
| **Department Manager** | Team leads / dept heads | Approve team leave & overtime, conduct performance reviews |
| **Finance** | Payroll / accounting staff | Run payroll cycles, pay salaries, export financial reports |
| **Employee** | Everyone else | Self-service: attendance, leave requests, payslips, training info |

---

## 3. Module Access Matrix

✔ = available · ➖ = limited · — = hidden

| Module | Super Admin | HR Manager | HR Officer | Manager | Finance | Employee |
|---|---|---|---|---|---|---|
| Dashboard | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ |
| Departments | Full | Full | View + Edit¹ | — | — | — |
| Positions | Full | Full | View + Edit¹ | — | — | — |
| Employees | Full | Full² | Create + Edit³ | View | View | View |
| Documents | ✔ | ✔ | ✔ | — | — | — |
| Recruitment | Full | Full | Full | — | — | — |
| Onboarding | ✔ | ✔ | ✔ | — | — | — |
| Shifts | Full | Full | Full | View | — | View |
| Attendance | Full⁴ | Full⁴ | Full⁴ | View | — | View |
| Leave | Full | Full | Full | View + Approve | — | View + Request |
| Overtime | Full | Full | Full | View + Approve | — | View + Request |
| Payroll | Full | Full⁵ | View only⁶ | — | Full cycle | — |
| Payslips | Generate | Generate | Generate | View | Generate | View |
| Performance | Full | Full | View | Full | — | View |
| Training | Full | Full | Full | View | — | View |
| Assets | Full | Full | Assign | View | View | — |
| Separation | ✔ | ✔ | — | — | — | — |
| Reports | Export | Export | Export | View only | Export | — |
| Audit Log | ✔ | View only | — | — | — | — |
| Settings | Full | — | — | — | — | — |
| User Accounts | ✔ | — | — | — | — | — |
| Notifications 🔔 | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ |

> ¹ Can edit existing departments/positions but **cannot create new ones**.
> ² Includes deactivating employees (soft delete).
> ³ Cannot deactivate/delete employees.
> ⁴ Includes check-in/out, corrections, and correction approval.
> ⁵ Cannot change system settings or users.
> ⁶ Can view payroll and generate/download payslip PDFs, but cannot calculate/review/approve/pay.

---

## 4. Super Administrator

### What You See

Every sidebar module, plus **Settings** (with the **User Accounts** tab) and **Audit Log**.

### Key Tasks

#### 4.1 Manage User Accounts (Settings → User Accounts)

Only you can do this.

1. Open **Settings** from the sidebar → **User Accounts** tab.
2. **Create account** — enter username, full name, email and an initial password (stored BCrypt-hashed, never plaintext).
3. **Assign role** — attach one or more roles (Super Admin, HR Manager, HR Officer, Manager, Finance, Employee). The role determines everything the user sees.
4. **Reset password** — sets the *must-change-password* flag; the user must pick a new one at next login.
5. **Deactivate account** — blocks login without deleting history.

#### 4.2 Configure the System (Settings)

Tabs: Company · Payroll · Attendance · Leave · Documents · General.

Common changes:

| Setting | Effect |
|---|---|
| `company.name`, address, phone, email, logo | Shown on reports and payslip PDFs |
| `payroll.overtime_rate_multiplier` | OT rate (default **1.5×**) |
| `payroll.tax_rate_percent` | Income tax (default **5%**) |
| `payroll.social_security_*_percent` | Employee 2% / Employer 3% |
| `payroll.working_days_per_month` | Daily-rate basis (default **22**) |
| `attendance.default_shift_code` | Shift used when employee has none |
| `leave.carry_forward_enabled` | Annual-leave carry-over toggle |
| `documents.expiry_warning_days` | Expiry warning window (default **30 days**) |
| `app.timezone` | Business timezone for attendance math |

Settings save **all-or-nothing** in one transaction, and old/new values are recorded in the Audit Log.

#### 4.3 Review the Audit Log

- Filter by user, action, module, date range, keyword; double-click a row for full detail (including device/IP info).
- The log is append-only — nobody, including you, can edit or delete entries.

### You Should Also Know

You inherit every capability described in the HR Manager chapter below.

---

## 5. HR Manager

### What You See

Everything except Settings/User Accounts. Unlike the HR Officer, you **can** create departments/positions, deactivate employees, register assets, and read the Audit Log.

### Typical Responsibilities

#### 5.1 Own the Hiring Pipeline (Recruitment)

Follow the five tabs left to right:

```
Vacancy → Candidate → Application → Interview → Offer → Hire
```

1. **New Vacancy** — job title, department, position, headcount, employment type, salary range, dates → **Open Vacancy**.
2. **New Candidate** — personal data, skills, source; optionally attach a resume.
3. **New Application** — link candidate to an OPEN vacancy → **Shortlist** → **Schedule Interview**.
4. **Record Result** after each interview (PASS/FAIL/ON_HOLD, score 0–100).
5. On PASS: **Create Offer** → Send → Accept → **Hire Candidate**. Hiring automatically creates the employee record and marks the vacancy FILLED — in one transaction.

#### 5.2 Final HR Approval on Leave

Leave needs two approval levels. Managers do step 1; you do step 2:

1. Right-click a PENDING request → **Approve (Manager)**.
2. Then **Approve (HR – Final)** — balance is deducted here.
3. Or **Reject** / allow the requester to **Cancel** while pending.

#### 5.3 Conduct Performance Reviews

Same rights as Department Managers (see §7.3): create draft → score criteria → submit for feedback → record feedback → **Finalize** (locks the review and computes the weighted score).

#### 5.4 Handle Separations

Resignations and terminations live here exclusively (plus Super Admin):

- **New Resignation** — notice period is calculated automatically; submit → approve → **Process Exit Checklist**, which in one transaction: sets status RESIGNED, closes shift assignments, returns assets, voids draft payrolls.
- **Terminations** take effect immediately after double confirmation and become read-only.

#### 5.5 Register Company Assets

Unlike HR Officers, you can create/edit/retire assets (**ASSET_MANAGE**), not just assign and return them.

#### 5.6 Monitor with Audit Log

Read-only access to who-did-what — useful for investigating discrepancies before escalating to the Super Admin.

---

## 6. HR Officer

### What You See

Operational modules: Departments (view/edit), Positions (view/edit), Employees, Documents, Recruitment, Onboarding, Shifts, Attendance, Leave, Overtime, Payroll (view + payslips), Performance (view), Training, Assets (assign), Reports.

### Daily Workflows

#### 6.1 Register New Employees

1. **Employees → New Employee**.
2. Fill required fields (*): code, gender, names, join date, employment type, department, position (filtered by chosen department), basic salary.
3. **Save** — errors appear in a red banner.
4. Upload documents via the profile's **Documents** tab (NRC copy, contract, certificates…) with type and expiry date; expiring documents trigger automatic warnings 30 days ahead.

#### 6.2 Run Daily Attendance

1. Open **Attendance**, pick the date.
2. **Check In / Check Out** selected employees — late/early/worked/OT are computed against the person's assigned shift.
3. **Mark Absentees** generates rows for active employees with no record that day.
4. Fix mistakes via right-click → **Correct…** — a **reason is mandatory** and is logged.
5. Approve correction requests when required (**ATTENDANCE_CORRECTION_APPROVE**).

#### 6.3 Process Leave & Overtime Requests

- Employees submit; a manager approves level 1; **you give the final HR approval** (or reject).
- The form shows the employee's **available balance** before submission; approvals deduct from it automatically.
- Overtime amounts are snapshotted at approval time using the current salary (rate × hours × multiplier).

#### 6.4 Recruit and Onboard

Full recruitment rights (see §5.1) plus **Onboarding**:

1. Templates tab defines checklist items (10 preloaded tasks ship with the system).
2. Pick an employee → **Generate Checklist** → track progress (`N/M done`).
3. Mark tasks Completed / Skipped / Waived, or Reopen them.

#### 6.5 Assign Shifts and Assets

- **Shifts → Assignments → Assign Shift** — effective-dated; previous assignment auto-closes and history is preserved.
- **Assets** — right-click an AVAILABLE asset → **Assign to Employee…**; later **Return Asset…** (condition DAMAGED sends the asset to repair). Overdue returns get flagged.

#### 6.6 Payslips and Reports

- In **Payroll** you may **download payslip PDFs** for APPROVED/PAID rows, but calculation/review/approval/payment belongs to Finance.
- Reports: generate any of the 14 report types and export to PDF/Excel or print.

### What You Cannot Do

- Create departments or positions (edit only).
- Deactivate/delete employees.
- Calculate, review, approve or mark payroll paid.
- Conduct performance reviews (view only).
- Access Settings, User Accounts, or the Audit Log.

---

## 7. Department Manager

### What You See

Employees (view), Shifts (view), Attendance (view), Leave, Overtime, Performance (full), Training (view), Assets (view), Reports (no export), and your own payslips via PAYSLIP_VIEW.

### Key Tasks

#### 7.1 Approve Team Leave (Step 1 of 2)

1. Open **Leave → Requests**, find PENDING rows for your team members.
2. Right-click → **Approve (Manager)** — this records the first approval level.
3. HR completes the final approval afterwards; balances are deducted at that point.
4. You may also **Reject** with justification.

#### 7.2 Approve Team Overtime

1. **Overtime** module → PENDING rows.
2. Right-click → **Approve** or **Reject**.
3. Rate/h and amount columns populate at approval (hourly base × multiplier × hours).

#### 7.3 Conduct Performance Reviews

1. **Performance → Reviews → New Review** — employee, period, comments → **Create Draft**.
2. Right-click → **Score Criteria** — score each criterion 1–5 (step 0.5); weights come from the Criteria tab.
3. **Submit for Feedback** → later **Record Feedback…** with the employee's comments.
4. **Finalize** — computes the weighted overall score (X / 5) and permanently locks the review.
5. Cancel is possible only before finalization.

### What You Cannot Do

Edit employees, manage shifts/attendance, request leave on behalf of others beyond self-service rules, touch payroll, or export reports (viewing only). If you need an export, ask HR or Finance.

---

## 8. Finance

### What You See

Employees (view), Payroll (full cycle), Payslip generation, Reports with export, Assets (view).

### Monthly Payroll Cycle

Open **Payroll** — the current-month period is created automatically.

| Step | Button | Result |
|---|---|---|
| 1 | **Calculate** | Creates payroll rows for all employees (DRAFT → CALCULATED). Gross = basic + allowances + bonuses + approved OT; deductions = tax + social security + others. |
| 2 | **Review All** | CALCULATED → REVIEWED |
| 3 | **Approve All** | REVIEWED → APPROVED (rows become immutable) |
| 4 | **Mark Paid** | APPROVED → PAID |

- Rows can be processed individually via right-click instead of bulk actions.
- **Download Payslip** on APPROVED/PAID rows saves a PDF to the Desktop.
- Approved+ payroll can never be edited; corrections require a new period adjustment agreed with HR.
- Terminated employees' unapproved drafts are auto-voided by the exit checklist.

### Financial Reporting

Generate **Payroll Report**, **Salary Report**, **Overtime Report**, etc., filter by period/department/status, then **Export PDF**, **Export Excel**, or **Print**. Every export is written to the audit trail.

---

## 9. Employee (Self-Service)

### What You See

Dashboard, Employees (directory, view-only), Shifts (your schedule), Attendance (your records), Leave, Overtime, Payslips, Performance, Training — plus the notification bell.

### Key Tasks

#### 9.1 Check Your Records

- **Attendance** — verify your check-in/out times and late/OT calculations per day; use Monthly View for totals.
- **Shifts** — confirm your currently assigned shift and its times.
- **Payslips** — downloadable/viewable once Finance approves and marks payroll paid.

#### 9.2 Request Leave

1. **Leave → New Request**.
2. Choose leave type (Annual 18d, Sick 14d, Casual 7d, Maternity 90d, Paternity 15d, Unpaid 30d, Other 5d), start/end dates, and a mandatory reason.
3. The form shows **Available: N day(s)** — requests exceeding balance are rejected.
4. Submit → status PENDING. Track approval (Manager → HR) in the list; **Cancel Request** yourself while still pending.

#### 9.3 Request Overtime

1. **Overtime → Request Overtime**.
2. Date, hours (**0.01–12 only**), mandatory reason → Submit.
3. Watch for manager approval; payment posts to that month's payroll automatically once approved.

#### 9.4 Stay Informed

- Notification bell shows unread count (refreshes every 60 seconds): approvals, birthdays, training reminders, document warnings.
- **Performance** — read finalized reviews and scores.
- **Training** — see programs you are enrolled in and results.

### What You Cannot Do

Anything administrative. No editing of attendance (ask HR for corrections), no approving anything, no payroll access beyond your own payslips.

### Directory Scope

The Employees directory is scoped for you: a plain Employee account sees **only its own record** (matched via your account email), and the profile's data tabs show just your own attendance, leave, payslips, reviews and trainings.

---

## 10. Shared Features (All Roles)

| Feature | How To Use |
|---|---|
| Theme toggle 🌙/☀️ | Header button switches Light/Dark mode |
| Notifications 🔔 | Click bell → Mark as Read / Mark All Read; bold rows are unread |
| Refresh | `F5` refreshes the current module |
| Context menus | Right-click any table row for available actions |
| Double-click | Opens detail dialogs (profiles, audit entries) |
| Confirmations | Destructive actions always ask first; terminations ask twice |
| Toasts | Small popups confirm success/failure of each action |
| Required fields | Marked with `*` |
| Sidebar collapse | ☰ hamburger button |

---

## 11. Quick Troubleshooting by Role

| Symptom | Applies To | Fix |
|---|---|---|
| "Access denied" panel | Everyone | Your role lacks that module — contact the Super Admin |
| "Too many failed attempts…" | Everyone | Temporary lockout; wait, then retry |
| Forced password dialog at login | Everyone | Admin reset your password — choose a new one to continue |
| Missing **Settings** menu | HR Manager, others | Settings is Super-Admin-only |
| Cannot create a department/position | HR Officer | Creation is restricted to HR Manager and above; edit existing ones instead |
| **Mark Absentees** does nothing | HR Officer | All active employees already have records for that date |
| Leave shows negative/unavailable balance | Employees, Approvers | Used + pending + requested cannot exceed entitlement; use Unpaid/Other type or wait for release |
| Rate/h & Amount show `-` | Managers, HR | Columns fill in at approval time |
| **Calculate** disabled in Payroll | HR Officer | Payroll execution is Finance/Super-Admin territory |
| Payslip download fails | Everyone | Record must be APPROVED or PAID first |
| Export buttons disabled | Managers | REPORT_EXPORT is not in your role; ask HR/Finance |
| Review won't accept edits | Managers, HR | Finalized reviews are locked permanently |
| Asset won't assign | HR | Only AVAILABLE assets can be assigned; RETIRED/LOST are terminal |
| Data looks stale | Everyone | Press `F5`, or logout/login; dashboards auto-refresh on data changes |

---

*© 2026 AMS — HR Management System v1.0.0 · Role-based companion to `USER_MANUAL_MM.md`.*
