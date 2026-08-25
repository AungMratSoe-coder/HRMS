# HR Management System — User Types (Roles) နှင့် Authorization စနစ်

**Version:** 1.0.0  
**Application Type:** Desktop Application (Java Swing + MySQL)  
**Package:** `com.ams.hrms.security`

> ဤစာတမ်းတွင် System ထဲရှိ user အမျိုးအစား (role) ၆ မျိုး၊ role တစ်ခုချင်းစီ၏ ခွင့်ပြုချက် (permission) များနှင့် authorization စစ်ဆေးပုံကို ရှင်းပြထားသည်။

---

## စာရင်းအညွှန်း (Table of Contents)

1. RBAC Model အနှစ်ချုပ်
2. Role ၆ မျိုး အနှစ်ချုပ်
3. Authorization လုပ်ဆောင်ပုံ (နည်းပညာအသေးစိတ်)
4. Role တစ်ခုချင်းစီ အသေးစိတ်
5. Permission × Role Matrix အပြည့်အစုံ
6. Sidebar Menu ပေါ်မှု (Menu Visibility)
7. Default Accounts
8. Role / Permission အသစ် ထည့်သွင်းပုံ

---

## ၁။ RBAC Model အနှစ်ချုပ်

System သည် **RBAC (Role-Based Access Control)** ကို အသုံးပြုသည် —

```
User  →  user_roles  →  Role  →  role_permissions  →  Permission
```

- Permission သည် **action-level** ဖြစ်သည် (ဥပမာ — `PAYROLL_APPROVE`, `EMPLOYEE_CREATE`) — စုစုပေါင်း **51** ခု ရှိသည်
- Role သည် permission အစုံတစ်ခု ဖြစ်သည် — role ၆ မျိုး ရှိသည်
- User တစ်ဦးတွင် role **တစ်ခုထက် ပို၍** ရှိနိုင်သည် (`user_roles` many-to-many) — ဆိုလိုသည်မှာ effective permissions သည် role အားလုံး၏ **union** ဖြစ်သည်
- Permission များကို module အလိုက် အုပ်စုခွဲထားသည် — EMPLOYEE, ORG, SHIFT, ATTENDANCE, LEAVE, OVERTIME, PAYROLL, RECRUITMENT, ONBOARDING, PERFORMANCE, TRAINING, ASSET, REPORT, SEPARATION, SYSTEM

**အဓိက စည်းမျဉ်း** — UI မှ menu/button ဖျောက်ရုံဖြင့် လုံခြုံမှုမဟုတ်ပါ။ Authorization ကို **service layer တွင်** အမြဲတမ်း ပြန်စစ်သည်။

---

## ၂။ Role ၆ မျိုး အနှစ်ချုပ်

| Role Code | အမည် | ရည်ရွယ်ချက် | Permission အရေအတွက် |
|---|---|---|---|
| `SUPER_ADMIN` | Super Administrator | System တစ်ခုလုံးကို ကန့်သတ်ချက်မရှိ စီမံနိုင်သည် | **51** (အားလုံး) |
| `HR_MANAGER` | HR Manager | ဝန်ထမ်း lifecycle တစ်ခုလုံးကို စီမံပြီး HR operations များကို approve လုပ်သည် | **49** (system admin မှလွဲ၍ အားလုံး) |
| `HR_OFFICER` | HR Officer | နေ့စဉ် HR လုပ်ငန်း — records, attendance, leave, recruitment | **38** |
| `MANAGER` | Department Manager | Team ၏ leave/overtime approve နှင့် performance review လုပ်သည် | **13** |
| `FINANCE` | Finance | Payroll တွက်/စစ်/ပေးချေခြင်း နှင့် financial reports | **10** |
| `EMPLOYEE` | Employee | Self-service — ကိုယ့် attendance, leave, payslip, training | **10** |

---

## ၃။ Authorization လုပ်ဆောင်ပုံ (နည်းပညာအသေးစိတ်)

### ၃.၁ Database Structure

`V1__schema.sql` + `V2__seed.sql` တွင် table ၅ ခုဖြင့် တည်ဆောက်ထားသည် —

```
users (id, username, password_hash, ...)
  └─ user_roles (user_id, role_id)          ← many-to-many
       └─ roles (role_code, role_name, is_system)
            └─ role_permissions (role_id, permission_id)   ← many-to-many
                 └─ permissions (perm_code, perm_name, module, description)
```

### ၃.၂ Login ဖြစ်ပြီးနောက် ဖြစ်စဉ်

```
1. AuthService.login()
     ├─ BCrypt password verify (timing-safe)
     ├─ LoginAttemptGuard (brute-force lockout)
     └─ user ၏ role အားလုံးမှ permission codes များကို DB မှ load
2. SessionContext.login()
     └─ Immutable session snapshot ထည့်သည်
        (username, fullName, mustChangePassword, Set<Permissions>)
        - DB မှာ မသိသော permission code ရှိပါက log ထား၍ ချန်လှပ်သည်
          (build အဟောင်း မပျက်ဘဲ migration ဖြင့် permission အသစ်ထည့်နိုင်ရန်)
3. Logout ဖြစ်ပါက session clear
```

Permission စစ်ဆေးမှုသည် memory ထဲက session snapshot ကိုသာ ကြည့်သည် — action တိုင်းတွင် DB query မလုပ်ပါ။

### ၃.၃ Enforcement Layer ၃ ခု

| Layer | ဖိုင် | လုပ်ဆောင်ချက် |
|---|---|---|
| **Service layer (မဖြစ်မနေရ)** | `SecurityService.require()` / `requireAny()` / `requireAll()` | Permission မရှိပါက `AuthorizationException` ပြန်သည် — business logic မလုပ်မီ ရပ်သည် |
| **Navigation** | `MenuDefinition.visibleTo()` + `SidebarMenuPanel` | Permission မပါသော module menu ကို sidebar မှ ဝှက်သည် |
| **UI convenience** | `SecureButton` | Permission မရှိလျှင် button ကို disable — enforcement မဟုတ်၊ convenience သက်သက်ဖြစ်သည် |

```java
// Service method တိုင်း၏ ပုံစံ (ဥပမာ)
public void approve(long payrollId) {
    SecurityService.require(Permissions.PAYROLL_APPROVE);   // gate
    // ... business logic
    auditService.record(...);                                // audit
}
```

---

## ၄။ Role တစ်ခုချင်းစီ အသေးစိတ်

### ၄.၁ SUPER_ADMIN — Super Administrator

- Permission **အားလုံး (51)** ရှိသည်
- တစ်ဦးတည်းသော **System module** ဝင်ရောက်ခွင့်ရှိသော role ဖြစ်သည်:
  - `USER_MANAGE` — user account ဖန်တီး/reset/activate + role ချထားး
  - `SETTINGS_MANAGE` — company info, currency, overtime multiplier စသည့် settings
  - `AUDIT_LOG_VIEW` — audit trail ကြည့်ခွင့်
- Default account: `admin` (အောက်ပါ အခန်း ၇ တွင်)

### ၄.၂ HR_MANAGER — HR Manager

- `SUPER_ADMIN` မှ `SETTINGS_MANAGE` နှင့် `USER_MANAGE` နှစ်ခုကို ဖယ်ထားသမျှ **အားလုံး (49)**
- ဆိုလိုသည်မှာ — Employee/Documents, Organization, Shifts, Attendance, Leave, Overtime, **Payroll အပြည့်အစုံ**, Recruitment, Onboarding, Performance, Training, Assets, Reports, **Separation**, **Audit Log** တို့ကို စီမံနိုင်သည်
- User account/role စီမံခွင့်နှင့် system settings ပြင်ခွင့် မရှိပါ

### ၄.၃ HR_OFFICER — HR Officer

နေ့စဉ် HR operational လုပ်ငန်းများ လုပ်ရသူ —

| လုပ်နိုင်သည် | မလုပ်နိုင်သည် |
|---|---|
| Employee create/update/photo, documents စီမံ | Employee **delete** (deactivate) |
| Shift ဖန်တီး/assign, attendance မှတ်/ပြင်/correction approve | Department/Position **create** (view+update သာ) |
| Leave/Overtime request + approve + cancel | **Payroll calculate/review/approve/mark-paid** |
| Recruitment pipeline အပြည့်အစုံ (vacancy → offer) | **Performance review** conduct (view သာ) |
| Onboarding checklist, Training စီမံ | Asset **register/retire** (view+assign သာ) |
| Payroll records **ကြည့်ခွင့်** + payslip ထုတ်ခွင့် | Audit log, Separation, System module |

### ၄.၄ MANAGER — Department Manager

Team leadership scope (13 permissions) —

- **Approve လုပ်သူ**: Leave approve, Overtime approve
- **Review လုပ်သူ**: Performance review ဖန်တီး/finalize (`PERFORMANCE_MANAGE` ရှိသော တစ်ခုတည်းသော non-HR role)
- **ကြည့်ခွင့်**: Employees, Shifts, Attendance, Leave, Overtime, Training, Assets, Reports, Payslips (team ၏)
- ⚠️ သတိ: seed တွင် `LEAVE_REQUEST` **မပါပါ** — manager ကိုယ်တိုင် leave တောင်းခွင့် မရှိ (လိုအပ်ပါက role ထပ်တပ်ရန် အခန်း ၈ ကြည့်ပါ)

### ၄.၅ FINANCE — Finance

Payroll ownership + financial reporting (10 permissions) —

- **Payroll state machine အပြည့်အစုံ**: `PAYROLL_CALCULATE → PAYROLL_REVIEW → PAYROLL_APPROVE → PAYROLL_MARK_PAID`
- Payslip **generate** (`PAYSLIP_GENERATE`) — သို့သော် payslip list ကြည့်ခွင့် (`PAYSLIP_VIEW`) မပါ
- Reports run + PDF/Excel export
- Employee/Asset **ကြည့်ခွင့်သာ**
- HR operations (leave approve, recruitment ...) များ လုံးဝ မပါဝင်

### ၄.၆ EMPLOYEE — Employee

Self-service (10 permissions) —

- ကိုယ့်အကြောင်း ကြည့်ခွင့် (`EMPLOYEE_VIEW`)
- ကိုယ့် attendance / shift / leave balance / overtime / payslip / training / performance review ကြည့်ခွင့်
- Leave request + Overtime request **တောင်းခွင့်**
- Approve လုပ်ခွင့်၊ data ပြင်ခွင့် လုံးဝ မရှိပါ

---

## ၅။ Permission × Role Matrix အပြည့်အစုံ

✅ = ခွင့်ပြု · ❌ = ခွင့်မပြု
(SA = SUPER_ADMIN, HRM = HR_MANAGER, HRO = HR_OFFICER, MGR = MANAGER, FIN = FINANCE, EMP = EMPLOYEE)

### Employees & Documents

| Permission | SA | HRM | HRO | MGR | FIN | EMP |
|---|:-:|:-:|:-:|:-:|:-:|:-:|
| `EMPLOYEE_VIEW` | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| `EMPLOYEE_CREATE` | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ |
| `EMPLOYEE_UPDATE` | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ |
| `EMPLOYEE_DELETE` | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ |
| `EMPLOYEE_PHOTO_UPLOAD` | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ |
| `DOCUMENT_MANAGE` | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ |

### Organization (Departments / Positions)

| Permission | SA | HRM | HRO | MGR | FIN | EMP |
|---|:-:|:-:|:-:|:-:|:-:|:-:|
| `DEPARTMENT_VIEW` | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ |
| `DEPARTMENT_CREATE` | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ |
| `DEPARTMENT_UPDATE` | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ |
| `POSITION_VIEW` | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ |
| `POSITION_CREATE` | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ |
| `POSITION_UPDATE` | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ |

### Shifts

| Permission | SA | HRM | HRO | MGR | FIN | EMP |
|---|:-:|:-:|:-:|:-:|:-:|:-:|
| `SHIFT_VIEW` | ✅ | ✅ | ✅ | ✅ | ❌ | ✅ |
| `SHIFT_MANAGE` | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ |
| `SHIFT_ASSIGN` | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ |

### Attendance

| Permission | SA | HRM | HRO | MGR | FIN | EMP |
|---|:-:|:-:|:-:|:-:|:-:|:-:|
| `ATTENDANCE_VIEW` | ✅ | ✅ | ✅ | ✅ | ❌ | ✅ |
| `ATTENDANCE_CREATE` | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ |
| `ATTENDANCE_UPDATE` | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ |
| `ATTENDANCE_CORRECTION_APPROVE` | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ |

### Leave

| Permission | SA | HRM | HRO | MGR | FIN | EMP |
|---|:-:|:-:|:-:|:-:|:-:|:-:|
| `LEAVE_VIEW` | ✅ | ✅ | ✅ | ✅ | ❌ | ✅ |
| `LEAVE_REQUEST` | ✅ | ✅ | ✅ | ❌ | ❌ | ✅ |
| `LEAVE_APPROVE` | ✅ | ✅ | ✅ | ✅ | ❌ | ❌ |
| `LEAVE_CANCEL` | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ |

### Overtime

| Permission | SA | HRM | HRO | MGR | FIN | EMP |
|---|:-:|:-:|:-:|:-:|:-:|:-:|
| `OVERTIME_VIEW` | ✅ | ✅ | ✅ | ✅ | ❌ | ✅ |
| `OVERTIME_REQUEST` | ✅ | ✅ | ✅ | ❌ | ❌ | ✅ |
| `OVERTIME_APPROVE` | ✅ | ✅ | ✅ | ✅ | ❌ | ❌ |

### Payroll & Payslips

| Permission | SA | HRM | HRO | MGR | FIN | EMP |
|---|:-:|:-:|:-:|:-:|:-:|:-:|
| `PAYROLL_VIEW` | ✅ | ✅ | ✅ | ❌ | ✅ | ❌ |
| `PAYROLL_CALCULATE` | ✅ | ✅ | ❌ | ❌ | ✅ | ❌ |
| `PAYROLL_REVIEW` | ✅ | ✅ | ❌ | ❌ | ✅ | ❌ |
| `PAYROLL_APPROVE` | ✅ | ✅ | ❌ | ❌ | ✅ | ❌ |
| `PAYROLL_MARK_PAID` | ✅ | ✅ | ❌ | ❌ | ✅ | ❌ |
| `PAYSLIP_VIEW` | ✅ | ✅ | ✅ | ✅ | ❌ | ✅ |
| `PAYSLIP_GENERATE` | ✅ | ✅ | ✅ | ❌ | ✅ | ❌ |

### Recruitment

| Permission | SA | HRM | HRO | MGR | FIN | EMP |
|---|:-:|:-:|:-:|:-:|:-:|:-:|
| `RECRUITMENT_VIEW` | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ |
| `RECRUITMENT_MANAGE` | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ |
| `INTERVIEW_MANAGE` | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ |
| `OFFER_MANAGE` | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ |

### Onboarding / Performance / Training

| Permission | SA | HRM | HRO | MGR | FIN | EMP |
|---|:-:|:-:|:-:|:-:|:-:|:-:|
| `ONBOARDING_MANAGE` | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ |
| `PERFORMANCE_VIEW` | ✅ | ✅ | ✅ | ✅ | ❌ | ✅ |
| `PERFORMANCE_MANAGE` | ✅ | ✅ | ❌ | ✅ | ❌ | ❌ |
| `TRAINING_VIEW` | ✅ | ✅ | ✅ | ✅ | ❌ | ✅ |
| `TRAINING_MANAGE` | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ |

### Assets

| Permission | SA | HRM | HRO | MGR | FIN | EMP |
|---|:-:|:-:|:-:|:-:|:-:|:-:|
| `ASSET_VIEW` | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ |
| `ASSET_ASSIGN` | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ |
| `ASSET_MANAGE` | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ |

### Reports / Separation / System

| Permission | SA | HRM | HRO | MGR | FIN | EMP |
|---|:-:|:-:|:-:|:-:|:-:|:-:|
| `REPORT_VIEW` | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ |
| `REPORT_EXPORT` | ✅ | ✅ | ✅ | ❌ | ✅ | ❌ |
| `SEPARATION_MANAGE` *(V4 မှ ထည့်)* | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ |
| `USER_MANAGE` | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |
| `SETTINGS_MANAGE` | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |
| `AUDIT_LOG_VIEW` | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ |

---

## ၆။ Sidebar Menu ပေါ်မှု (Menu Visibility)

Menu တစ်ခုစီသည် `MenuDefinition.java` တွင် required permission တစ်ခုစီနှင့် ချိတ်ထားသည် — permission မရှိလျှင် menu ပေါ်မည်မဟုတ်ပါ။

| Menu | လိုအပ်သော Permission | မြင်ရသော Roles |
|---|---|---|
| Departments | `DEPARTMENT_VIEW` | SA, HRM, HRO |
| Positions | `POSITION_VIEW` | SA, HRM, HRO |
| Employees | `EMPLOYEE_VIEW` | **Roles အားလုံး** |
| Recruitment | `RECRUITMENT_VIEW` | SA, HRM, HRO |
| Onboarding | `ONBOARDING_MANAGE` | SA, HRM, HRO |
| Attendance | `ATTENDANCE_VIEW` | SA, HRM, HRO, MGR, EMP |
| Shifts | `SHIFT_VIEW` | SA, HRM, HRO, MGR, EMP |
| Overtime | `OVERTIME_VIEW` | SA, HRM, HRO, MGR, EMP |
| Leave | `LEAVE_VIEW` | SA, HRM, HRO, MGR, EMP |
| Payroll | `PAYROLL_VIEW` | SA, HRM, HRO, FIN |
| Performance | `PERFORMANCE_VIEW` | SA, HRM, HRO, MGR, EMP |
| Training | `TRAINING_VIEW` | SA, HRM, HRO, MGR, EMP |
| Assets | `ASSET_VIEW` | SA, HRM, HRO, MGR, FIN |
| Documents | `DOCUMENT_MANAGE` | SA, HRM, HRO |
| Separation | `SEPARATION_MANAGE` | SA, HRM |
| Reports | `REPORT_VIEW` | SA, HRM, HRO, MGR, FIN |
| Audit Log | `AUDIT_LOG_VIEW` | SA, HRM |
| Settings | `SETTINGS_MANAGE` | SA |

(Dashboard ကိုမူ signed-in user အားလုံး မြင်ရသည်)

---

## ၇။ Default Accounts

| Account | Password | Role | မှတ်ချက် |
|---|---|---|---|
| `admin` | `Admin@123` | SUPER_ADMIN | `V2__seed.sql` မှ seed — password သည် BCrypt hash (cost 12) အဖြစ်သာ သိမ်းသည် |

- `application.properties` ရှိ `app.login.hint` သည် development credential hint ဖြစ်ပြီး **production build တွင် ဖျက်ရမည်**
- `RbacSmokeTool` (dev tool) က `officer` / `Officer@123` (HR_OFFICER) test account ကို လိုအပ်ပါက provision လုပ်သည် — production နှင့် မသက်ဆိုင်ပါ
- Admin reset လုပ်ထားသော account သည် `mustChangePassword` flag ကြောင့် ပထမ login တွင် password ပြောင်းရမည်

---

## ၈။ Role / Permission အသစ် ထည့်သွင်းပုံ

Permission အသစ်ထည့်ရန် — Flyway migration အသစ်တစ်ခု ရေးပါ (`V4__separation_permission.sql` ကို ပုံစံယူပါ):

```sql
-- 1. Permission အသစ် ထည့်
INSERT INTO permissions (perm_code, perm_name, module, description)
VALUES ('NEW_PERMISSION', 'New permission', 'MODULE', 'description');

-- 2. Role များကို grant လုပ်
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p
WHERE p.perm_code = 'NEW_PERMISSION'
  AND r.role_code IN ('SUPER_ADMIN', 'HR_MANAGER');
```

```java
// 3. Permissions enum တွင် code ထည့် (V2 seed နှင့် name တူရမည်)
public enum Permissions {
    ...
    NEW_PERMISSION,
}
```

```java
// 4. Service method တွင် gate ထည့်
SecurityService.require(Permissions.NEW_PERMISSION);
```

- Build အဟောင်းသည် DB တွင် မသိသော code ကို login အချိန်တွင် ချန်လှပ်သည် — breaking မဖြစ်ပါ
- User တစ်ဦးတွင် role အသစ် ထပ်တပ်ရန် — Settings → User Accounts (`USER_MANAGE` လိုသည်) မှ လုပ်နိုင်သည်; user_roles table တွင် row ထည့်ခြင်းဖြစ်သည်

---

## Appendix — ဖိုင်အနေအထား

```
src/main/java/com/ams/hrms/security/
├── Permissions.java       ← 51 permission codes (enum)
├── SecurityService.java   ← require / requireAny / requireAll gates
├── SessionContext.java    ← login session + permission set (immutable)
├── AuthService.java       ← login flow, timing-safe verify
├── LoginAttemptGuard.java ← brute-force lockout
└── PasswordHasher.java    ← BCrypt (cost 12)

src/main/resources/db/migration/
├── V1__schema.sql         ← users/roles/permissions tables
├── V2__seed.sql           ← role definitions + permission grants + admin account
└── V4__separation_permission.sql ← migration pattern ဥပမာ

src/main/java/com/ams/hrms/ui/main/MenuDefinition.java ← menu ↔ permission mapping
```
