# HR Management System — Flowcharts

> Open `FLOWCHARTS.drawio` at [app.diagrams.net](https://app.diagrams.net) or with the
> draw.io VS Code extension for the full editable diagrams.
> The Mermaid versions below render directly on GitHub/GitLab and in many Markdown viewers.

---

## 1. Application Startup & Login

```mermaid
flowchart TD
    A([Run java -jar hr-management-system-1.0.0.jar]) --> B[Load configuration<br/>application.properties / env vars]
    B --> C{MySQL server<br/>reachable?}
    C -- No --> D[[STARTUP FAILED message]] --> E([Exit code 1])
    C -- Yes --> F[Initialize connection pool<br/>HikariCP]
    F --> G[Flyway migrations<br/>first run creates schema + seed data]
    G --> H[Initialize services + install theme]
    H --> I[Open Login window]
    I --> J[/Enter username + password/]
    J --> K{Account locked?<br/>5 failures within 30 s}
    K -- Yes --> L["Too many failed attempts.<br/>Try again in N seconds."] --> J
    K -- No --> M{Credentials valid?}
    M -- No --> N[Show inline error banner] --> J
    M -- Yes --> O[Create session + load role permissions]
    O --> P([Open Main Window<br/>role-based sidebar])
```

---

## 2. Recruitment Workflow

```mermaid
flowchart LR
    V[New Vacancy<br/>OPEN] --> S1[Application submitted<br/>SUBMITTED]
    S1 --> S2[Shortlist<br/>SCREENING]
    S2 --> IV[Schedule Interview<br/>PENDING]
    IV --> RES{Record Result?}
    RES -- ON_HOLD --> IV
    RES -- FAIL --> RJ[REJECTED<br/>reason mandatory]
    RES -- PASS --> OD[Create Draft Offer<br/>DRAFT]
    OD --> OS[Send Offer<br/>SENT]
    OS -- Decline / Expire / Cancel --> DC[DECLINED / EXPIRED / CANCELLED]
    OS --> OA[Accept Offer<br/>ACCEPTED]
    OA --> HI[Hire Candidate<br/>creates Employee record]
    HI --> ONB([Onboarding starts])
    S2 -.-> RJ
    IV -.-> WD[WITHDRAWN]

    note[Vacancy states: OPEN ⇄ ON_HOLD → FILLED / CLOSED / CANCELLED<br/>Candidate pipeline: NEW → SHORTLISTED → INTERVIEWING → OFFERED → HIRED]
```

---

## 3. Leave Approval (two-level) & Overtime Approval

```mermaid
flowchart TD
    subgraph LEAVE [Leave — two-level approval]
        L1[/Employee submits leave request<br/>PENDING/] --> L2{Manager approval?}
        L2 -- Yes --> L3{HR final approval?}
        L3 -- Yes --> L4[APPROVED<br/>days deducted from balance]
        L2 -- No --> L5[REJECTED]
        L3 -- No --> L5
        L1 -.Cancel Request.-> L6[CANCELLED]
    end

    subgraph OVERTIME [Overtime — single approval]
        T1[/Overtime requested<br/>hours 0.01-12 + reason<br/>PENDING/] --> T2{Approve overtime?}
        T2 -- Yes --> T3[APPROVED<br/>rate + amount snapshot saved]
        T3 --> T4[Included in next Payroll calculation<br/>OT multiplier 1.5x]
        T2 -- No --> T5[REJECTED]
    end
```

**Seeded leave types:** Annual 18 (carry-forward max 5) · Sick 14 · Casual 7 · Maternity 90 (F) · Paternity 15 (M) · Unpaid 30 · Other 5

---

## 4. Payroll Lifecycle

```mermaid
flowchart LR
    P1[Period auto-created<br/>current month<br/>DRAFT] --> P2[Calculate<br/>CALCULATED]
    P2 --> P3[Review All<br/>REVIEWED]
    P3 --> P4[Approve All<br/>APPROVED]
    P4 --> P5[Mark Paid<br/>PAID]
    P4 -. also once APPROVED .-> PS[/Download Payslip<br/>PDF to Desktop/]
    P5 --> PS
    P3 -. Cancel .-> CX[CANCELLED]

    NF[Gross = Basic + Allowances + OT x1.5<br/>Net = Gross - Tax 5% - SSC employee 2%<br/>Employer SSC 3% · 22 working days · USD]
```

---

## 5. Employee Lifecycle (Hire to Separation)

```mermaid
flowchart TD
    H([Hired via Recruitment]) --> OB1[Generate Onboarding Checklist<br/>from active templates]
    OB1 --> OB2[Complete mandatory tasks]
    OB2 --> ACT([ACTIVE employee])

    ACT --> OPS[Ongoing operations:<br/>attendance · shifts · leave · overtime<br/>payroll · performance reviews · training · assets]
    OPS --> SEP{Separation initiated?}

    SEP -- Resignation --> R1[Resignation submitted<br/>SUBMITTED] --> R2{Approved by HR?}
    R2 -- Yes --> R3[Process Exit Checklist:<br/>set RESIGNED · close shifts · return assets · void draft payroll]
    R2 -. Rejected / Withdrawn .-> ACT

    SEP -- Termination --> T1[Termination recorded<br/>immediate effect · double confirm<br/>category + rehire flag]

    R3 --> END([RESIGNED / TERMINATED<br/>employee inactive])
    T1 --> END
```

---

## 6. Attendance Daily Cycle

```mermaid
flowchart TD
    A[Mark Absentees generates rows<br/>for employees without records] --> B{Employee action}
    B -- Check In --> C[Record created<br/>late minutes computed vs shift grace]
    C --> D{Check Out}
    B -- absent --> E[Status ABSENT]
    D --> F[Worked hours / early leave / OT computed]
    F --> G{Discrepancy found?}
    G -- Yes --> H[Correct...<br/>reason mandatory + audited]
    G -- No --> I[Final daily record<br/>PRESENT / LATE / EARLY_LEAVE / HALF_DAY ...]
    H --> I
```
