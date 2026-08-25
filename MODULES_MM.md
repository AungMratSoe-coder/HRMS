# HR Management System — Module များ လုပ်ဆောင်ပုံ ရှင်းလင်းချက် (Developer Guide)

**Version:** 1.0.0  
**Application Type:** Desktop Application (Java Swing + MySQL)  
**Package:** `com.ams.hrms`

> ဤစာတမ်းသည် Developer များအတွက် ရည်ရွယ်၍ module တစ်ခုချင်းစီ၏ အတွင်းပိုင်း လုပ်ဆောင်ပုံကို ရှင်းပြထားသည်။ အသုံးပြုသူအတွက် လမ်းညွှန်ကို `USER_MANUAL_MM.md` တွင် ကြည့်ပါ။

---

## စာရင်းအညွှန်း (Table of Contents)

1. Architecture အနှစ်ချုပ်
2. Startup လုပ်ငန်းစဉ် (`config`, `db`)
3. Event Bus (`event`)
4. Exception စနစ် (`exception`)
5. Security နှင့် RBAC (`security`)
6. Repository Layer နှင့် SQL Helper (`repository`)
7. Authentication / User Accounts
8. Dashboard
9. Departments / Positions (Organization)
10. Employees
11. Documents
12. Recruitment
13. Onboarding
14. Shifts
15. Attendance
16. Leave
17. Overtime
18. Payroll နှင့် Payslip
19. Performance
20. Training
21. Assets
22. Separation
23. Notifications
24. Reports
25. Audit Log
26. Settings
27. Reusable UI Components (`component`, `ui.theme`, `util`)
28. Testing ချဉ်းကပ်ပုံ (`service/*Rules`, `tools`)

---

## ၁။ Architecture အနှစ်ချုပ်

Application ကို **layered architecture** ဖြင့် တည်ဆောက်ထားသည်။ Feature တစ်ခုစီ (ဥပမာ - Leave) တွင် package များခွဲ၍ code ရေးထားသည် —

```
ui.leave.LeavePanel            ← Screen rendering, user input (Swing)
        │  calls
controller.*Controller         ← Navigation + screen wiring
        │  calls
service.LeaveService           ← Business rules + RBAC gate + audit
        │  uses                     (pure logic များကို LeaveRules တွင် ခွဲထား)
repository.LeaveRepository     ← SQL အားလုံး (PreparedStatement only)
        │  JDBC (HikariCP pool)
MySQL (hrms database)          ← Schema ကို Flyway migration ဖြင့် စီမံ
```

**အရေးကြီး စည်းမျဉ်းများ —**

| စည်းမျဉ်း | ရှင်းလင်းချက် |
|---|---|
| Pure logic ခွဲထုတ်ခြင်း | တွက်ချက်မှု/စည်းမျဉ်းများ (`*Rules`, `*Calculator`) ကို database/UI မပါဝင်သော class သီးသန့်အဖြစ် ရေးထားပြီး unit test အပြည့်အစုံ ရှိသည် |
| Service layer တွင် RBAC | Permission စစ်ဆေးခြင်းကို service မှာလုပ်သည် — UI မှ button ဖျောက်ရုံဖြင့် လုံခြုံမှုမဟုတ်ပါ |
| Audit အားလုံး | Data ပြောင်းလဲသည့် operation တိုင်း `AuditService` မှတဆင့် မှတ်တမ်းတင်သည် |
| Module ချင်းချိတ်ဆက်မှု လျှော့ခြင်း | Module များ တစ်ခုကိုတစ်ခု reference မထားဘဲ `EventBus` ဖြင့် တုံ့ပြန်ကြသည် |

---

## ၂။ Startup လုပ်ငန်းစဉ် (`config`, `db`)

`Main.main()` → `config.Bootstrapper.launch()` မှ အောက်ပါအဆင့်များကို အစီအစဉ်တကျ လုပ်ဆောင်သည် —

```
1. AppConfig.get()             → application.properties + Environment Variable ဖတ်
                                 (${ENV_VAR} placeholder များကို resolve; HRMS_DB_URL,
                                  HRMS_DB_USER, HRMS_DB_PASSWORD override လက်ခံ)
2. DbChecker.check()           → MySQL ကို SELECT VERSION() probe;
                                 error type အလိုက် လူနားလည်လွယ် message ပြ
                                 (server down / password မှား / database မရှိ)
3. DatabaseConfig.initialize() → HikariCP connection pool စတင်
4. DatabaseMigrator.migrate()  → Flyway ဖြင့် V1__schema.sql ... V4 အစီအစဉ်တကျ apply;
                                 ပထမအကြိမ်ဆိုလျှင် hrms database ကို အလိုအလျောက် ဖန်တီး
5. ServiceRegistry.initialize()→ Service အားလုံးကို constructor injection ဖြင့် ချိတ်ဆက်
                                 (Spring framework မသုံးပါ — manual DI)
6. ThemeManager.install()      → FlatLaf Light/Dark theme တပ်ဆင်
7. LoginFrame ဖွင့်             → EDT (Event Dispatch Thread) ပေါ်တွင်
```

JVM shutdown ဖြစ်ပါက shutdown hook မှ connection pool ကို လုံခြုံစွာ ပိတ်ပေးသည်။

---

## ၃။ Event Bus (`event`)

`EventBus` သည် **in-process publish/subscribe system** ဖြစ်သည် —

```java
// Subscribe (screen load ဖြစ်သည့်အခါ)
EventBus.subscribe(Events.DataChanged.class, e -> loadRows());

// Publish (data ပြင်ပြီးပါက)
EventBus.publish(new Events.DataChanged("employees"));
```

- Delivery အားလုံးကို **EDT ပေါ်တွင်** လုပ်ဆောင်သည် — thread-safe ဖြစ်စေရန် `ConcurrentHashMap` + `CopyOnWriteArrayList` သုံးထားသည်
- ဥပမာ — Leave request approve လုပ်လျှင် `DataChanged("leave")` publish သည်။ Dashboard panel က listener ထား၍ ချက်ချင်း refresh ဖြစ်သည်။ Panels များ တစ်ခုကိုတစ်ခု တိုက်ရိုက်ခေါ်စရာမလိုတော့ပါ
- Background daemon thread လိုအပ်သော `NotificationService` dispatch ကလွဲ၍ delivery ကို EDT ပေါ်မှာပင် လုပ်သည်

---

## ၄။ Exception စနစ် (`exception`)

Exception အားလုံးသည် `HrmsException` base class မှ ဆင်းသက်သည် —

| Exception | အသုံး |
|---|---|
| `ValidationException` | Input မှားယွင်းခြင်း (field-level message ပါသည်) |
| `BusinessException` | စည်းမျဉ်းချိုးဖြတ်မှု (ဥပမာ — balance မလုံလောက်) |
| `AuthenticationException` | Login မအောင်မြင် / account lock |
| `AuthorizationException` | Permission မရှိသော action |
| `DataAccessException` | Database error (technical detail + user-friendly message **နှစ်ခုစလုံး** သိမ်းသည်) |
| `ConfigurationException` | Setting/config မှားယွင်းခြင်း |

Exception တစ်ခုစီတွင် **developer message** နှင့် **user message** ၂ မျိုး ပါဝင်သည် — log file တွင် technical အပြည့်အစုံ၊ dialog တွင် လူနားလည်လွယ်သော စာသားသာ ပြသည်။ `ErrorHandler` က central မှ catch ၍ Toast/dialog ပြသည်။

---

## ၅။ Security နှင့် RBAC (`security`)

Login အောင်မြင်ပါက `SessionContext` တွင် **immutable snapshot** တစ်ခု ထည့်သည် —

```
LoginFrame → AuthService.login(username, password)
    ├─ PasswordHasher.verify()      ← BCrypt (at.favre.lib)
    ├─ unknown username ဖြစ်လျှင်ပါ  ← timing attack တားဆီးရန်
    │     time ညီမျှသော dummy verify လုပ်သည်
    ├─ LoginAttemptGuard            ← fail ၅ ကြိမ်ဆက်တိုက်ဆိုပါက account lock
    ├─ account state စစ်             ← ACTIVE မဟုတ်ပါက ပယ်ဖျက်
    └─ SessionContext.login(user)   ← permission set အားလုံးကို login တစ်ခါ
                                      load; logout တွင် clear
```

- `Permissions` class တွင် permission constant များရှိသည် (`EMPLOYEE_CREATE`, `PAYROLL_APPROVE`, `AUDIT_LOG_VIEW` ...)
- Service method တိုင်း၏ အစတွင် `SecurityService.require(Permissions.X)` gate ရှိသည်
- Role hierarchy (display အလို): SUPER_ADMIN → HR_MANAGER → HR_OFFICER → MANAGER → FINANCE → EMPLOYEE
- DB တွင် `users / roles / permissions / user_roles / role_permissions` table ၅ ခုဖြင့် many-to-many mapping ဖြင့် သိမ်းသည်
- `mustChangePassword = true` ဖြစ်နေပါက login ပြီးပါက forced password-change dialog ပေါ်သည်

---

## ၆။ Repository Layer နှင့် SQL Helper (`repository`)

- `Sql` class သည် raw JDBC ကို wrap သည် — query အားလုံး `PreparedStatement` parameter binding ဖြင့် ရေးသည် (**SQL injection ကာကွယ်ပြီးသား**)
- Dynamic WHERE clause များ (Report/Audit filters) တွင် column name များကို code ထဲမှ whitelist switch ဖြင့်သာ တပ်ဆင်သည် — user input ကို SQL text ထဲ concatenate မလုပ်ပါ
- `SQLException` ဖမ်းပါက `DataAccessException` (brief SQL + friendly message) အဖြစ် translate သည်
- Transaction လိုအပ်သည့် operation များ (hire, separation processing) တွင် `Sql` ၏ transaction API ဖြင့် all-or-nothing လုပ်သည်

---

## ၇။ Authentication / User Accounts

**Files:** `service/AuthService`, `service/UserService`, `security/*`, `ui/login/LoginFrame`

| လုပ်ငန်း | လုပ်ဆောင်ပုံ |
|---|---|
| Login | အပေါ်ပိုင်း အခန်း ၅ တွင် ဖော်ပြပြီး |
| Logout | SessionContext clear + `LOGOUT` audit entry |
| Account ဖန်တီး | `UserService` — `USER_MANAGE` permission လိုသည်; default password ကို BCrypt hash ဖြင့်သာ သိမ်း |
| Password reset | Admin က reset လုပ်ပါက `mustChangePassword` flag တင်သည် |
| Activate/Deactivate | Deactivate ဖြစ်နေသော account login မဝင်နိုင် |
| Role ချထား | user_roles junction table တွင် insert/delete |

Audit actions: `LOGIN`, `LOGIN_FAILED`, `LOGOUT`, `PASSWORD_CHANGE` များ အလိုအလျောက် မှတ်သည်။

---

## ၈။ Dashboard

**Files:** `service/DashboardService`, `repository/DashboardRepository`, `dto/DashboardStats|DashboardData|CategoryCount|TrendDay`, `ui/dashboard/*`

- Business rule မရှိ — **read-only aggregation** သက်သက်ဖြစ်သည်
- Query များစွာကို repository မှတစ်ကြိမ်တည်း ခေါ်၍ `DashboardData` record တစ်ခုတည်းအဖြစ် ပြန်ပေးသည် (background thread မှ ခေါ်ရန် ဒီဇိုင်းပြုလုပ်ထားသည်)
- ပါဝင်သည်များ — headcount stats, department-wise category counts, attendance trend (TrendDay), payroll trend (PayrollTrendPoint)
- Chart rendering ကို JFreeChart + `ChartTheme` (light/dark) ဖြင့် လုပ်သည်

---

## ၉။ Departments / Positions (Organization)

**Files:** `service/DepartmentService`, `service/PositionService`, `ui/org/*`

Department နှင့် Position နှစ်ခုစလုံးတွင် pattern တူသည် —

1. **Uniqueness** — code/name ထပ်နေမရ (`CREATE`/`UPDATE` နှစ်ခုစလုံးတွင် စစ်သည်)
2. **Referential guard** — Department အောက်မှာ active employee/position ရှိနေသေးလျှင် deactivate မရ (data integrity rule); Position ကိုလည်း employee သုံးနေသေးလျှင် မပိတ်ရ
3. **Salary envelope** — Position တွင် min/max salary range ရှိပြီး Employee ခန့်အပ်သည့်အခါ basic_salary သည် အဲဒီ range အတွင်း ရှိရမည်
4. RBAC gate + audit entry တိုင်း

---

## ၁၀။ Employees

**Files:** `service/EmployeeService`, `service/EmployeeRules`, `repository/EmployeeRepository`, `model/Employee`, `ui/employee/*`

- **EmployeeRules (pure logic)** — gender (`MALE/FEMALE/OTHER`), employment type vocabulary, contact format, join-date logic (future date မရ), self-manager guard (ကိုယ့်ကိုကိုယ် manager အဖြစ် မထားရ), position salary-envelope စစ်ဆေးမှု
- **EmployeeService (database-backed rules)** — employee code နှင့် NRC uniqueness, soft-delete status transitions (`ACTIVE → RESIGNED/TERMINATED/INACTIVE`), photo storage, history
- **Immutable history** — အရေးကြီး field တစ်ခုပြောင်းပါက `employee_history` table တွင် အရင်တန်ဖိုး မှတ်တမ်းချန်ထားသည် — ဖျက်မရပါ
- Search/filter ကို paginated query ဖြင့် လုပ်သည် (large dataset အတွက်)

---

## ၁၁။ Documents

**Files:** `service/DocumentService`, `util/FileStorage`, `repository/EmployeeDocumentRepository`, `ui/documents/DocumentsPanel`

- File ကိုယ်တိုင်ကို **filesystem** (`HRMS_DOCUMENTS_ROOT`) တွင် သိမ်းပြီး metadata (type, expiry date, uploaded by ...) ကို MySQL တွင် သိမ်းသည်
- Upload validation — type သည် whitelist (`NRC, PASSPORT, CONTRACT, CERTIFICATE, RESUME, TRAINING_CERTIFICATE, OTHER`) အတွင်း ရှိရမည် + size limit ရှိသည်
- **Expiry tracking** — 30 ရက်အတွင်း သက်တမ်းကုန်မည့် document များကို Notification module က warning ပေးသည်
- Archive/soft-delete ဖြင့် ဖျက်သည် — physical file မပျက်ပါ

---

## ၁၂။ Recruitment

**Files:** `service/RecruitmentService` (project ၏ အကြီးဆုံး service), `service/RecruitmentWorkflow`, `ui/recruitment/*`

**State machines (RecruitmentWorkflow — pure logic):**

```
Vacancy:     OPEN ⇄ ON_HOLD;  active → FILLED / CLOSED / CANCELLED
Candidate:   NEW → SHORTLISTED → INTERVIEWING → OFFERED → HIRED
Application: SUBMITTED → SCREENING → INTERVIEW → OFFER → ACCEPTED
Offer:       DRAFT → SENT → ACCEPTED / DECLINED / EXPIRED / CANCELLED
```

- Transition တစ်ခုချင်းစီကို `RecruitmentWorkflow` မှ အတည်ပြုသည် — မှားသော transition ကို runtime တွင် `BusinessException` ပြန်သည်
- Interview rounds — application တစ်ခုအတွက် round များစွာ ရှိနိုင် (count query ဖြင့် next round number တွက်)
- **Transactional hire** — Offer accepted ဖြစ်ပါက candidate → employee conversion ကို transaction တစ်ခုတည်းထဲ လုပ်သည် (vacancy FILLED, candidate HIRED, employee record အသစ်)

---

## ၁၃။ Onboarding

**Files:** `service/OnboardingService`, `dto/OnboardingProgress`, `ui/onboarding/*`

- ဝန်ထမ်းအသစ်တစ်ဦး ခန့်အပ်ခံရသည့်အခါ **active template တစ်ခုစီအတွက် task တစ်ခု** အလိုအလျောက် ဖန်တီးပေးသည် (due date = default 14 ရက်)
- Task lifecycle: `PENDING → COMPLETED / SKIPPED / WAIVED` — reopen ပြုလုပ်နိုင်သည်
- Progress ကို completion percentage အဖြစ် တွက်သည် (`OnboardingProgress` DTO)
- Template edit လုပ်ပါက **အနာဂတ် hire အသစ်များအတွက်သာ** သက်ရောက်သည် — ရှိပြီးသား checklist များကို rewrite မလုပ်ပါ

---

## ၁၄။ Shifts

**Files:** `service/ShiftService`, `ui/shift/*`, tables `shifts`, `employee_shifts`

- Shift definition — start/end time, break minutes, grace minutes; **overnight shift** (ဥပမာ 22:00 → 06:00) ကို support လုပ်သည်
- Assignment ကို **effective-dated** ထားသည် — employee တစ်ဦးတွင် open-ended record တစ်ခုသာ ရှိရမည်
- Shift အသစ် assign လုပ်ပါက record အရင် open-ended ကို **auto-close** သည် (end_date = new start − 1 day)
- ပြောင်းလဲမှုတိုင်း audit + employee history နှစ်ခုလုံး မှတ်သည်

---

## ၁၅။ Attendance

**Files:** `service/AttendanceService`, `service/AttendanceCalculator`, `ui/attendance/*`, table `attendance`

Math အားလုံးကို **AttendanceCalculator (pure logic)** တွင် ထားသည် —

```
offsets = RAW minutes relative to check-in (early arrival ဆို negative)
late    = scheduled_start + grace ကိုကျော်မှ late; early arrival က late မဟုတ်
early   = check-out နှင့် scheduled end ကြား gap
overnight shift ဖြစ်ပါက scheduled duration ကို midnight across roll လုပ်
```

**AttendanceService ၏တာဝန်များ —**

1. Check-in/check-out — employee ၏ **effective shift** (assignment date အလိုက်) နှင့် တိုက်စစ်
2. Correction — manual fix လုပ်ပါက recomputation ပြန်တွက်သည်
3. **Daily sweep** — နေ့စဉ် absent records / weekend records ကို batch ဖြင့် ဖန်တီး

---

## ၁၆။ Leave

**Files:** `service/LeaveService`, `service/LeaveRules`, `repository/LeaveRepository`, `ui/leave/*`, tables `leave_types`, `leave_requests`, `leave_approvals`, `leave_balances`

**LeaveRules (pure logic):**
- Day counting = inclusive (start နှင့် end နှစ်ရက်စလုံးပါဝင်)
- Overlap predicate — range နှစ်ခုသည် boundary day များ inclusive ဖြင့် ထပ်နေလျှင် overlap; SQL `LeaveRepository.overlaps` နှင့် logic တူညီသည်
- Max request = 366 ရက်

**Balance ledger:**

```
PENDING submit  → pending += days
Approve         → pending -= days, used += days
Reject/Cancel   → pending -= days  (release)
Balance check   → used + pending + requested ≤ entitlement မဖြစ်ရ
```

Approval workflow တွင် level (who approved) ကို `leave_approvals` တွင် မှတ်တမ်းတင်သည်။

---

## ၁၇။ Overtime

**Files:** `service/OvertimeService`, `service/OvertimeRules`, `ui/overtime/*`, table `overtime_requests`

**Formula (OvertimeRules):**

```
hourly_base = basic_salary ÷ working_days_per_month ÷ 8
rate        = hourly_base × multiplier        (2 dp)
amount      = hours × rate                    (2 dp, HALF_UP)
```

- multiplier ကို `app_settings` မှ ဖတ်သည် (configurable)
- Rate ကို **approval အချိန်မှာ snapshot** လုပ်သည် — salary နောက်မှ ပြောင်းလဲပါက approved amount မထိခိုက်ပါ
- Request → Approve/Reject workflow + audit

---

## ၁၈။ Payroll နှင့် Payslip

**Files:** `service/PayrollService`, `service/PayrollCalculator`, `service/PayslipService`, `report/PayslipPdfGenerator`, `ui/payroll/*`

**PayrollCalculator (pure arithmetic):**

```
gross     = basic + allowances + bonuses + overtime
deduction = tax% × taxable + social_security% × gross + other_deductions
net       = gross − total_deduction
```

**PayrollService (state machine):**

```
CALCULATED → REVIEWED → APPROVED → PAID
```

| Rule | ရှင်းလင်းချက် |
|---|---|
| Rule 6 | Employee/period တစ်ခုလျှင် payroll **တစ်ခုတည်း** (DB unique key + service check) |
| Rule 7 | APPROVED အထက် ရောက်ပြီး payroll ကို **edit မရ** (immutable) |
| Column interpolation | `transition()` တွင် column name ကို switch whitelist မှသာ ရွေး (`REVIEWED→reviewed`, `APPROVED→approved`, `PAID→paid`) |

**Payslip** — payroll data + company settings (name, address) ကို PDFBox ဖြင့် PDF ဆွဲ၍ documents directory တွင် သိမ်းသည်။

---

## ၁၉။ Performance

**Files:** `service/PerformanceService`, `service/PerformanceScoreCalculator`, `ui/performance/*`

**Workflow:**

```
DRAFT (MANAGER_REVIEW) → IN_PROGRESS (EMPLOYEE_FEEDBACK) → COMPLETED (FINALIZED)
                                    ↘ CANCELLED (anytime, early exit)
```

- Manager က criterion တစ်ခုချင်းစီကို weight ပါ 1–5 score ပေးသည်
- Employee feedback phase တွင် ဝန်ထမ်းက self-comment ရေးနိုင်သည်
- **Finalize လုပ်သည့်အခါ** overall score ကို တွက်၍ record freeze ဖြစ်သည်:

```
overall = Σ(score × weight) ÷ Σ(weight of scored items)
          clamp 1..5, round HALF_UP to 2 dp
```

(Weight မပေးထားသော criterion များကို denominator မှ ချန်သည် — normalized weighting)

Finalized review = immutable history ဖြစ်သည်။

---

## ၂၀။ Training

**Files:** `service/TrainingService`, `service/TrainingRules`, `ui/training/*`

- **Program** (`PLANNED/ONGOING/COMPLETED/CANCELLED`) — capacity နှင့်အတူ
- **Session** (`SCHEDULED/ONGOING/COMPLETED/CANCELLED`) — duration ကို start/end မှ auto-compute
- **Enrollment** — employee တစ်ဦး program တစ်ခုလျှင် တစ်ကြိမ်သာ; capacity ပြည့်နေပါက reject
- **Result recording** — `PASSED / FAILED` ကဲ့သို့ terminal outcome ရောက်ပါက **freeze** — ပြန်ပြင်မရတော့ပါ

Notification module က session စတင်မည့် 7 ရက်အလိုတွင် reminder ပို့သည်။

---

## ၂၁။ Assets

**Files:** `service/AssetService`, `service/AssetRules`, `ui/assets/*`

**Lifecycle states:**

```
AVAILABLE ⇄ ASSIGNED;  AVAILABLE → UNDER_REPAIR → AVAILABLE;  → RETIRED / LOST
```

- **Assignment = transactional pair** — `asset_assignments` row insert + asset status → ASSIGNED ကို transaction တစ်ခုတည်းထဲ လုပ်သည် (status mismatch မဖြစ်စေရန်)
- Return လုပ်သည့်အခါ condition အလိုက် routing — damaged ဆိုပါက asset → UNDER_REPAIR
- Lost handling + **overdue detection** (return due date ကျော်နေသော assignments ကို flag)
- Notes field တွင် issue/return history ကို append လုပ်သည်

---

## ၂၂။ Separation

**Files:** `service/SeparationService`, `service/SeparationRules`, `ui/separation/*`

**Resignation:** `SUBMITTED → APPROVED → PROCESSED` (သို့) `REJECTED / WITHDRAWN`; notice-period math ကို SeparationRules တွက်သည်  
**Termination:** record လုပ်သည့်ချက်ချင်း effective ဖြစ်သည်

**Process (exit checklist) — transaction တစ်ခုတည်းထဲ:**

```
1. employee.status → RESIGNED / TERMINATED   (history ထိန်းသိမ်း)
2. Open shift assignment ပိတ်
3. Open asset assignments ပြန်အပ် / assets release
4. DRAFT / CALCULATED payroll များ VOID
   (APPROVED+ ဖြစ်နေပြီးသားများကို မထိပါ — immutability rule)
```

---

## ၂၃။ Notifications

**Files:** `service/NotificationService`, `service/NotificationRules`, `repository/NotificationRepository`, `ui/notification/*`

**တာဝန် ၃ ရပ် —**

1. **Personal feed** — logged-in user ၏ list/unread count/mark-read (authenticated user မည်သူမဆို ကိုယ့် feed ကိုယ်ဖတ်နိုင်)
2. **Domain listeners** — bootstrap တွင် EventBus subscribe; leave/payroll events များကို recipient အသီးသီးဆီ fan-out; dispatch ကို **daemon thread** ပေါ်မှာ လုပ်သည် (EDT block မဖြစ်ရ)
3. **Operational scan** (idempotent + dedup):
   - Pending approval digest
   - Document expiry warnings (30 ရက်အလို)
   - Birthday notices
   - Training reminders (7 ရက်အလို)

Message wording အားလုံးကို `NotificationRules` (pure logic) မှ ဖန်တီးပြီး title/message length limits များ validate သည်။ Unread similar notification ရှိနေသေးလျှင် duplicate မဖန်တီးပါ။

---

## ၂၄။ Reports

**Files:** `service/ReportService`, `repository/ReportRepository`, `report/*`, `ui/reports/ReportsPanel`

- Generation အတွက် `REPORT_VIEW`; export/print အတွက် `REPORT_EXPORT` permission သီးသန့် လိုသည်
- Filter combination များကို query မခေါ်မီ validate သည်; day-level report များတွင် range ကို **366 ရက်** အထိသာ ခွင့်ပြုသည် (performance guard)
- Dynamic WHERE များကို repository မှ safe fragment များဖြင့် တည်ဆောက်သည်
- Export formats:

| Writer | Library |
|---|---|
| `PdfReportWriter` | Apache PDFBox |
| `ExcelReportWriter` | Apache POI (XSSF) |
| Print | Swing printable table |

Export တိုင်း audit entry မှတ်သည် (who exported what, when)။

---

## ၂၅။ Audit Log

**Files:** `service/AuditService`, `repository/AuditRepository`, `ui/audit/*`, table `audit_logs`

- **Write side** — service တိုင်းက မိမိ mutation များကို ဒီနေရာမှာ report လုပ်သည်; **audit failure က business operation ကို မရပ်စေရ** (fail-open design)
- **Read side** — privileged; `AUDIT_LOG_VIEW` permission ကို service layer မှာ စစ်သည် (UI မှ menu ဖျောက်ရုံဖြင့် မလုံခြုံဟူသော principle)
- Filter (user/action/date range/keyword) နှင့် pagination ဖြင့် ကြည့်နိုင်သည်

---

## ၂၆။ Settings

**Files:** `service/SettingsService`, `service/SettingsValidator`, `ui/settings/*`, table `app_settings`

- Seeded rows များ — company info, currency, timezone, overtime multiplier, attendance defaults ...
- Value validation ကို **type-driven** လုပ်သည် (SettingsValidator):
  - NUMBER — inclusive [min,max] range
  - CURRENCY — ISO 4217 code
  - TIMEZONE — IANA zone id
  - REQUIRED_KEYS — blank မဖြစ်ရ (`company.name`, `attendance.default_shift_code`)
- Changes များကို **transaction တစ်ခုတည်း** (all-or-nothing) apply ပြီး value အဟောင်း/အသစ် နှစ်ခုလုံးကို audit မှတ်သည်

---

## ၂၇။ Reusable UI Components (`component`, `ui.theme`, `util`)

Screen တိုင်းမှာ ပြန်သုံးနိုင်ရန် widget library တစ်ခု တည်ဆောက်ထားသည် —

| Component | အသုံး |
|---|---|
| `HrmsTable` | Sortable/paginated table wrapper |
| `PaginationPanel` | Page navigation |
| `SearchField` | Debounced search input |
| `FormField` | Label + input + error message |
| `DatePickerField` | Date picker |
| `Toast` | Non-blocking success/error feedback |
| `StatusBadge` | Color-coded status chip |
| `EmptyStatePanel` / `LoadingPanel` / `ErrorPanel` | State screens |
| `SidebarMenuPanel` | RBAC-aware navigation (permission မပါသော menu ကို ဝှက်) |
| `HeaderPanel` | User info + theme toggle + notifications |
| `ModernButton` | Styled buttons |

- **Theme** — `ThemeManager` + FlatLaf `.properties` files (Light/Dark); `Palette` မှ color tokens
- **UiThread** — EDT-safe helpers; **UiGraphics/ImageUtils** — photo crop/scale
- **FileStorage** — documents root directory management; **IconLoader** — SVG icons cache
- **Dialogs** — confirm/input dialogs standard styling

---

## ၂၈။ Testing ချဉ်းကပ်ပုံ

**Unit tests (`src/test/java`) — 19 test classes:**

Business logic အားလုံးကို *pure classes* များအဖြစ် ရေးထားသောကြောင့် database/UI မလိုဘဲ test ပြုလုပ်နိုင်သည် —

```
PayrollCalculatorTest, OvertimeRulesTest, LeaveRulesTest, EmployeeRulesTest,
AttendanceCalculatorTest, PerformanceScoreCalculatorTest, TrainingRulesTest,
AssetRulesTest, SeparationRulesTest, NotificationRulesTest,
RecruitmentWorkflowTest, SettingsValidatorTest, ValidatorsTest,
SecurityServiceTest, LoginAttemptGuardTest, EventBusTest,
AuditFilterTest, ReportWritersTest, OnboardingProgressTest
```

Run command:

```
mvn test
```

**Smoke tools (`com.ams.hrms.tools`) — 42 CLI tools:**

Real database နှင့် real UI နှင့် တကယ့် end-to-end scenario များ စစ်ဆေးသည် (ဥပမာ — `LoginSmokeTool` က wrong-password rejection, lockout, logout clearing တို့ကို စစ်သည်; `*ScreenSmokeTool` များက screenshot ရိုက်သည်)။ Dev-only tools များဖြစ်သည်။

---

## Appendix — Package Map

```
com.ams.hrms
├── Main                 → entry point
├── config               → AppConfig, Bootstrapper, DatabaseConfig, ServiceRegistry
├── db                   → DbChecker (probe), DatabaseMigrator (Flyway)
├── event                → EventBus, Events
├── exception            → HrmsException hierarchy, ErrorHandler
├── model                → 31 record/entity classes (table mirror)
├── dto                  → Dashboard/Onboarding/Payroll computation DTOs
├── repository           → 25 repositories + Sql helper
├── security             → Permissions, SessionContext, LoginAttemptGuard
├── service              → 35 services + pure *Rules/*Calculator classes
├── validator            → shared validators
├── report               → PDF/Excel writers, PayslipPdfGenerator
├── controller           → 21 controllers (navigation/wiring)
├── ui                   → 20+ feature packages + theme
│   ├── component        → reusable widgets
│   ├── theme            → ThemeManager, Palette
│   └── (login, main, dashboard, org, employee, recruitment, onboarding,
│        shift, attendance, leave, overtime, payroll, performance,
│        training, assets, documents, separation, reports, audit,
│        settings, notification)
├── util                 → Dialogs, FileStorage, IconLoader, ImageUtils,
│                          UiGraphics, UiThread
└── tools                → 42 smoke/CLI dev tools
```
