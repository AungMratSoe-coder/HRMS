# HR Management System — အသုံးပြုသူ လက်တွေ့လမ်းညွှန် (User Manual)

**Version:** 1.0.0  
**Application Type:** Desktop Application (Java Swing + MySQL)  
**Developer:** AMS

---

## စာရင်းအညွှန်း (Table of Contents)

1. နိဒါန်း (Introduction)
2. စနစ်လိုအပ်ချက်များ (System Requirements)
3. ထည့်သွင်းခြင်းနှင့် စတင်အသုံးပြုခြင်း (Installation & First Run)
4. စနစ်ထဲသို့ ဝင်ရောက်ခြင်း (Logging In)
5. ပင်မဝင်းဒိုး၏ တည်ဆောက်ပုံ (Main Window Layout)
6. Dashboard (မူလစာမျက်နှာ)
7. Departments (ဌာနများ စီမံခန့်ခွဲခြင်း)
8. Positions (ရာထူးများ စီမံခန့်ခွဲခြင်း)
9. Employees (ဝန်ထမ်းများ စီမံခန့်ခွဲခြင်း)
10. Recruitment (ဝန်ထမ်းခေါ်ယူရေး)
11. Onboarding (ဝန်ထမ်းအသစ် စတင်လိုက်ပါမှု)
12. Attendance (အလုပ်တက်မှု မှတ်တမ်း)
13. Shifts (အလုပ်ချိန်ဇယားများ)
14. Leave (အားလပ်ရက် တောင်းခံခြင်း)
15. Overtime (အလုပ်ပိုချိန်)
16. Payroll (လစာတွက်ချက်ခြင်း)
17. Performance (စွမ်းဆောင်ရည် သုံးသပ်ခြင်း)
18. Training (သင်တန်းများ)
19. Assets (ကုမ္ပဏီပစ္စည်းများ)
20. Separation (ဝန်ထမ်းခွင့်ပြုခြင်း/နုတ်ထွက်ခြင်း)
21. Reports (အစီရင်ခံစာများ)
22. Audit Log (စစ်ဆေးမှု မှတ်တမ်း)
23. Notifications (အသိပေးကြောင်းများ)
24. အသုံးပြုသူအခန်းကဏ္ဍများနှင့် ခွင့်ပြုချက်များ (Roles & Permissions)
25. အသုံးဝင်သတိပြုချက်များ (Tips)
26. ပြဿနာဖြေရှင်းခြင်း (Troubleshooting)

---

## ၁။ နိဒါန်း (Introduction)

**HR Management System** သည် ကုမ္ပဏီတစ်ခု၏ လူ့စွမ်းအားအရင်းအမြစ် (Human Resource) လုပ်ငန်းများကို တစ်နေရာတည်းမှ စီမံခန့်ခွဲနိုင်ရန် ရည်ရွယ်၍ တည်ဆောက်ထားသော Desktop Application ဖြစ်သည်။ ဤစနစ်ဖြင့် အောက်ပါလုပ်ငန်းများကို လုပ်ဆောင်နိုင်သည် -

- ဝန်ထမ်းများ၏ ကိုယ်ရေးအချက်အလက်၊ စာရွက်စာတမ်းများ မှတ်တမ်းတင်ခြင်း
- ဌာန၊ ရာထူးနှင့် လစာဖွဲ့စည်းပုံများ စီမံခန့်ခွဲခြင်း
- အလုပ်တက်မှု (Attendance)၊ အလုပ်ချိန်ဇယား (Shift)၊ အားလပ်ရက် (Leave)၊ အလုပ်ပိုချိန် (Overtime) များ ထိန်းသိမ်းခြင်း
- လစာတွက်ချက်ခြင်း၊ လစာပြစာ (Payslip) PDF ထုတ်ယူခြင်း
- အလုပ်ခေါ်ယူရေး (Recruitment) — လစ်လပ်ရာထူးမှ အလုပ်ချင်းပြုချက် (Offer) အထိ
- ဝန်ထမ်းအသစ် လိုက်နာရမည့် လုပ်ငန်းစဉ် (Onboarding Checklist)
- စွမ်းဆောင်ရည် သုံးသပ်ချက် (Performance Review) နှင့် သင်တန်း (Training)
- ကုမ္ပဏီပစ္စည်း (Asset) ချေးထားမှု မှတ်တမ်း
- အလုပ်မှ နုတ်ထွက်/ဖယ်ရှားခြင်း (Separation)
- အစီရင်ခံစာ (PDF/Excel) ထုတ်ယူခြင်းနှင့် ပရင့်ထုတ်ခြင်း
- လုပ်ငန်းတိုင်း၏ မှတ်တမ်း (Audit Log) ကြည့်ရှုခြင်း

---

## ၂။ စနစ်လိုအပ်ချက်များ (System Requirements)

| အချက်အလက် | လိုအပ်ချက် |
|---|---|
| Operating System | Windows 10/11, macOS, Linux |
| Java | **JDK/JRE 25** သို့မဟုတ် အထက် |
| Database | **MySQL 8.x** (localhost သို့မဟုတ် network server) |
| Build Tool | Apache Maven 3.9+ (program ကို ကိုယ်တိုင် build လုပ်မည်ဆိုပါက) |
| RAM | 4 GB သို့မဟုတ် အထက် (အကြံပြုချက်) |
| Screen Resolution | အနည်းဆုံး 1180 × 740 (1440 × 900 အကြံပြု) |

---

## ၃။ ထည့်သွင်းခြင်းနှင့် စတင်အသုံးပြုခြင်း (Installation & First Run)

### ၃.၁ Program ကို Build လုပ်ခြင်း

Project folder တွင် Command Prompt (သို့) PowerShell ဖွင့်၍ အောက်ပါ command ရိုက်ပါ —

```
mvn clean package
```

Build အောင်မြင်ပါက `target/hr-management-system-1.0.0.jar` ဆိုသော single executable file ရရှိမည်ဖြစ်သည်။

### ၃.၂ Database ချိတ်ဆက်ပုံ

Application သည် `src/main/resources/application.properties` file မှ configuration ဖတ်သည်။ မိမိ၏ environment နှင့် ကိုက်အောင် အောက်ပါ setting များကို ပြင်နိုင်သည် —

| Setting | ပုံသေတန်ဖိုး | ရှင်းလင်းချက် |
|---|---|---|
| `db.url` | `jdbc:mysql://localhost:3306/hrms...` | Database URL (`hrms` database ကို ပထမအကြိမ် run သည့်အခါ အလိုအလျောက် ဖန်တီးပေးသည်) |
| `db.username` | `root` | MySQL user |
| `db.password` | `password` | MySQL password |

> **လုံခြုံရေးအကြံပြုချက်** — Production တွင် password ကို file ထဲမထည့်ဘဲ Environment Variable များဖြင့် ပေးသင့်သည် -
> - `HRMS_DB_URL`
> - `HRMS_DB_USER`
> - `HRMS_DB_PASSWORD`
>
> ထို့အပြင် development credential hint ဖြစ်သော `app.login.hint` line ကို production build တွင် ဖျက်သင့်သည်။

### ၃.၃ Program ကို Run ခြင်း

```
java -jar target/hr-management-system-1.0.0.jar
```

### ၃.၄ ပထမအကြိမ် စတင်ခြင်း (First Run)

- Application စတင်သည့်အခါ Flyway migration များက လိုအပ်သော table များကို **အလိုအလျောက် ဖန်တီးပေးမည်** ဖြစ်သည်။
- ပုံသေ reference data များ (ဌာန ၆ ခု၊ ရာထူးများ၊ အားလပ်ရက်အမျိုးအစားများ၊ Shift ၄ မျိုး၊ နမူနာဝန်ထမ်း ၆ ဦး၊ ပစ္စည်းနမူနာများ၊ Performance criteria များ၊ Onboarding checklist template ၁၀ ခု) ကိုပါ ထည့်ပေးမည်ဖြစ်သည်။
- Console/log တွင် `Status: READY` ဟုပေါ်လာပါက စနစ်အသုံးပြုရန် အသင့်ဖြစ်သည်။

---

## ၄။ စနစ်ထဲသို့ ဝင်ရောက်ခြင်း (Logging In)

Program ဖွင့်လိုက်ပါက **Sign In window** ပေါ်လာမည်ဖြစ်သည်။

### ၄.၁ ပုံသေ Admin Account (Development)

| အကွက် | တန်ဖိုး |
|---|---|
| Username | `admin` |
| Password | `Admin@123` |

> ⚠️ ဤ account သည် development အတွက်သာ ဖြစ်ပြီး ပထမဆုံးဝင်ပြီးပါက စကားဝှက်ကို ချက်ချင်းပြောင်းလဲသင့်သည်။ (ဤဗားရှင်းတွင် password ပြောင်းရန် UI မပါဝင်သေးသဖြင့် Database administrator မှတဆင့် ပြောင်းလဲပါ)

### ၄.၂ ဝင်ရောက်ခြင်း အဆင့်ဆင့်

1. **Username** အကွက်တွင် မိမိ၏ အသုံးပြုသူအမည်ကို ရိုက်ထည့်ပါ။
2. **Password** အကွက်တွင် စကားဝှက်ကို ရိုက်ထည့်ပါ။ (👁️ icon ဖြင့် စကားဝှက်ကို ပြ/ဖျောက် ရွေးနိုင်သည်)
3. လိုအပ်ပါက **Remember username** checkbox ကို အမှန်ခြစ်ပါ — နောက်အကြိမ်ဖွင့်သည့်အခါ username ကို အလိုအလျောက် ဖြည့်ပေးမည်ဖြစ်သည်။
4. **Sign In** button ကို နှိပ်ပါ (သို့) **Enter** key နှိပ်ပါ။
5. ဝင်ရောက်မှု အောင်မြင်ပါက ပင်မဝင်းဒိုး (Main Window) ပေါ်လာမည်ဖြစ်သည်။

### ၄.၃ သတိပြုရန်

- Username သို့မဟုတ် password မှားပါက form အောက်တွင် အနီရောင် error message ပေါ်မည်ဖြစ်သည်။
- **၃၀ စက္ကန့်အတွင်း အကြိမ် ၅ ကြိမ် ထက်ပိုမှားပါက account သည် ခေတ္တပိတ်သွားမည်** ဖြစ်ပြီး "Too many failed attempts. Please try again in {N} seconds." ဟု ပြမည်။ စက္ကန့်အနည်းငယ် စောင့်ပြီးမှ ထပ်မံကြိုးစားပါ။

---

## ၅။ ပင်မဝင်းဒိုး၏ တည်ဆောက်ပုံ (Main Window Layout)

ဝင်ရောက်ပြီးပါက အဓိကအပိုင်း ၃ ပိုင်း ပါဝင်သည် —

### ၅.၁ Header (အပေါ်ဘား)

- **Module Title** — လက်ရှိဖွင့်ထားသော module အမည်
- **Theme Toggle** 🌙/☀️ — Light/Dark mode ရွေးချယ်နိုင်သည်
- **Notification Bell 🔔** — မဖတ်ရသေးသော အသိပေးကြောင်းအရေအတွက် badge ပြသည် (၆၀ စက္ကန့်တစ်ကြိမ် အလိုအလျောက် refresh; badge သည် ၉၉ ထက် မပို)
- **User Info** — မိမိ၏ အမည်နှင့် Role အမည်

### ၅.၂ Sidebar (ဘယ်ဘက် Menu)

မိမိ၏ Role တွင် ခွင့်ပြုချက်ရှိသော module များကိုသာ ပြမည်ဖြစ်သည် —

Dashboard · Departments · Positions · Employees · Recruitment · Onboarding · Attendance · Shifts · Overtime · Leave · Payroll · Performance · Training · Assets · Documents · Separation · Reports · Audit Log · Settings

- ☰ (hamburger) button ဖြင့် sidebar ကို ခေါက်/ဖြန့် လုပ်နိုင်သည်။
- Sidebar အောက်ခြေတွင် **Logout** ရှိသည် — နှိပ်ပါက "Are you sure you want to sign out?" confirmation မေးမည်ဖြစ်သည်။

### ၅.၃ Content Area (ဒေတာပြသည့်နေရာ)

Module တစ်ခုစီ၏ list, form, chart များ ပြသည်။

> **Keyboard shortcut** — `F5` key ဖြင့် လက်ရှိ module ကို refresh လုပ်နိုင်သည်။

> ℹ️ **မှတ်ချက်** — **Settings** menu ကို `SETTINGS_MANAGE` ခွင့်ပြုချက်ရှိသော user များသာ သုံးနိုင်ပြီး Company / Payroll / Attendance / Leave / Documents / General tab များဖြင့် စနစ်တန်ဖိုးများကို ပြင်ဆင်နိုင်သည်။ ဝန်ထမ်းစာရွက်စာတမ်းများကို **Documents** module (သို့) **Employee Profile → Documents tab** မှတစ်ဆင့် စီမံနိုင်သည်။

---

## ၆။ Dashboard (မူလစာမျက်နှာ)

ဝင်ရောက်သည့်အခါ ပထမဆုံးပေါ်သော စာမျက်နှာဖြစ်သည်။

### ၆.၁ ကိန်းဂဏန်း Cards ၈ ခု

| Card | ပြသသော အချက်အလက် |
|---|---|
| Total Employees | စုစုပေါင်း ဝန်ထမ်းအရေအတွက် |
| Active | လက်ရှိအလုပ်လုပ်နေသော ဝန်ထမ်း |
| New This Month | ဤလအတွင်း ဝင်သစ်ဝန်ထမ်း |
| On Leave Today | ဒီနေ့ အားလပ်ရက်ယူနေသူ |
| Present Today | ဒီနေ့ အလုပ်တက်နေသူ |
| Late Today | ဒီနေ့ နောက်ကျသူ |
| Absent Today | ဒီနေ့ ချွတ်ယွင်းသူ |
| Pending Leaves | စောင့်ဆိုင်းနေသော အားလပ်ရက်တောင်းခံလွှာ |

ထို့အပြင် **Latest Payroll** card တွင် နောက်ဆုံးလစာကာလ၏ Gross/Net ပမာဏ ပြသည်။

### ၆.၂ Chart များ

- **Employees by Department** — ဌာနအလိုက် ဝန်ထမ်းအရေအတွက် Bar Chart
- **Employee Status** — ဝန်ထမ်းအခြေအနေ Pie Chart
- **Attendance – Last 14 Days** — ၁၄ ရက်တာ တက်ရောက်မှု Line Chart (Present / Late / Absent)
- **Leave Usage This Year** — နှစ်စဉ်အားလပ်ရက် အသုံးပြုမှု Bar Chart
- **Payroll Cost by Period** — လစဉ်လစာကုန်ကျမှု Line Chart

Refresh icon (🔄) နှိပ်၍ dashboard ကို ပြန်လည်တွက်ချက်နိုင်သည်။ အခြား module တစ်ခုခု၌ data ပြောင်းလဲပါက dashboard သည် အလိုအလျောက် ပြန်တွက်ပေးသည်။

---

## ၇။ Departments (ဌာနများ စီမံခန့်ခွဲခြင်း)

**လိုအပ်ခွင့်ပြုချက်** — DEPARTMENT_VIEW (ကြည့်ရန်), DEPARTMENT_CREATE (အသစ်ထည့်ရန်), DEPARTMENT_UPDATE (ပြင်ဆင်ရန်)

### ဌာနအသစ် ထည့်သွင်းခြင်း

1. Sidebar မှ **Departments** နှိပ်ပါ။
2. **New Department** button နှိပ်ပါ။
3. Form တွင် အောက်ပါအတိုင်း ဖြည့်ပါ —

| Field | လိုအပ် | ရှင်းလင်းချက် |
|---|---|---|
| Code * | ✔ | ဌာနကုဒ် (ဥပမာ — IT, HR, FIN) |
| Department Name * | ✔ | ဌာနအမည် |
| Description | ✖ | ဌာန၏ တာဝန်နှင့် လုပ်ငန်းတာဝန်များ |
| Manager | ✖ | ဌာနမှူးရွေးချယ်ခြင်း (Active ဝန်ထမ်းများထဲမှ "- None -" ဖြင့် စတင်သည်) |
| Status | Edit တွင်သာ | ACTIVE / INACTIVE |

4. **Save** နှိပ်ပါ။

### အခြားလုပ်ဆောင်ချက်များ

- Row ပေါ်တွင် right-click ။ **Edit**, **Deactivate/Activate** ရွေးနိုင်သည်။
- Deactivate လုပ်ပါက ဌာနသည် အသစ်ရွေးချယ်မှုများတွင် မပေါ်တော့ပါ (data ကိုမူ မဖျက်ပါ)။
- ရှာဖွေရန် — "Search by name or code..." box တွင် ရိုက်ပါ။

---

## ၈။ Positions (ရာထူးများ စီမံခန့်ခွဲခြင်း)

**လိုအပ်ခွင့်ပြုချက်** — POSITION_VIEW / POSITION_CREATE / POSITION_UPDATE

### ရာထူးအသစ် ထည့်သွင်းခြင်း

1. Sidebar မှ **Positions** နှိပ်ပါ။
2. **New Position** button နှိပ်ပါ။
3. Form ဖြည့်ပါ —

| Field | လိုအပ် | ရှင်းလင်းချက် |
|---|---|---|
| Code * | ✔ | ရာထူးကုဒ် (ဥပမာ — IT-DEV) |
| Position Name * | ✔ | ရာထူးအမည် |
| Department * | ✔ | မည်သည့်ဌာနအောက်မှ ဖြစ်ကြောင်း ရွေးပါ |
| Minimum Salary | ✖ | လစာအနိမ့်ဆုံး range |
| Maximum Salary | ✖ | လစာအမြင့်ဆုံး range |
| Description | ✖ | ရာထူးတာဝန် ဖော်ပြချက် |

4. **Save** နှိပ်ပါ။ List တွင် Salary Range column ဖြင့် ပြမည်ဖြစ်သည် (ဥပမာ — `900 – 2,200`)။

> ရာထူးတစ်ခုသည် ဌာนတစ်ခုအောက်တွင်သာ ရှိနိုင်ပြီး Employee form ထဲတွင် ဌာနရွေးပြီးမှ ယင်းဌာန၏ ရာထူးများကိုသာ ရွေးချယ်နိုင်မည်ဖြစ်သည်။

---

## ၉။ Employees (ဝန်ထမ်းများ စီမံခန့်ခွဲခြင်း)

**လိုအပ်ခွင့်ပြုချက်** — EMPLOYEE_VIEW / EMPLOYEE_CREATE / EMPLOYEE_UPDATE / EMPLOYEE_DELETE / DOCUMENT_MANAGE

### ၉.၁ ဝန်ထမ်းအသစ် စာရင်းသွင်းခြင်း

1. Sidebar မှ **Employees** နှိပ်ပါ။
2. **New Employee** button နှိပ်ပါ။
3. Form တွင် အောက်ပါ field များ ဖြည့်ပါ —

| Field | လိုအပ် | ရှင်းလင်းချက် |
|---|---|---|
| Employee Code * | ✔ | ဝန်ထမ်းနံပါတ် (ဥပမာ — EMP-0007) |
| Gender * | ✔ | MALE / FEMALE / OTHER |
| First Name * / Last Name * | ✔ | နာမည် |
| Date of Birth | ✖ | မွေးသက္ကရာဇ် (Join Date ထက် စောရမည်) |
| NRC / National ID | ✖ | မှတ်ပုံတင်အမှတ် |
| Phone / Email | ✖ | ဆက်သွယ်ရန် |
| Address | ✖ | နေရပ်လိပ်စာ |
| Join Date * | ✔ | အလုပ်ဝင်ရက် (ပုံသေ — ဒီနေ့) |
| Employment Type * | ✔ | FULL_TIME / PART_TIME / CONTRACT / INTERN / PROBATION |
| Department * | ✔ | ဌာနရွေးပါ |
| Position * | ✔ | ရွေးထားသောဌာနအတွက် ရာထူးများသာ ပေါ်မည် |
| Manager | ✖ | တိုက်ရိုက်ချုပ်ကိုင်မှူး (optional) |
| Basic Salary * | ✔ | အခြေခံလစာ (ဂဏန်းသာ) |
| Status | Edit တွင်သာ | ACTIVE / INACTIVE |

4. **Save** နှိပ်ပါ။ Error ရှိပါက form အောက်ရှိ အနီရောင် banner တွင် ပြမည်ဖြစ်သည်။

### ၉.၂ စာရင်းစစ်ဆေးခြင်း

- **Search** — "Search by code, name or phone..." box ဖြင့် ရှာနိုင်သည်
- **Filter** — All Departments / All Positions / Status (ACTIVE, INACTIVE)
- **Pagination** — အောက်ခြေတွင် စာမျက်နှာခွဲ၍ ပြသည်
- Row ပေါ် **Double-click** = Profile ဖွင့်ခြင်း

### ၉.၃ Context Menu (Right-click) လုပ်ဆောင်ချက်များ

| လုပ်ဆောင်ချက် | ရှင်းလင်းချက် |
|---|---|
| View Profile | Profile Dialog ဖွင့်သည် |
| Edit | အချက်အလက်ပြင်ဆင်သည် |
| View History | ရာထူး/ဌာန/လစာ ပြောင်းလဲမှုမှတ်တမ်း ကြည့်သည် |
| Deactivate / Activate | ဝန်ထမ်းကို inactive ↔ active ပြောင်းသည် (confirmation မေးသည်) |

### ၉.၄ Employee Profile Dialog (Profile ကြည့်ခြင်း)

Profile dialog တွင် tab များပါဝင်သည် —

- **Profile** — ပုံ၊ အမည်၊ Code, Status, အလုပ်ဝင်သက်တမ်း၊ Personal/Contact/Employment/Salary information sections
- **Documents** — စာရွက်စာတမ်းများ Upload/Archive/Delete (အသေးစိတ် အောက်တွင်)
- **History** — ပြောင်းလဲမှုမှတ်တမ်း (Date, Change, From, To, Remarks)
- **Attendance / Leave / Payroll / Performance / Training / Assets** — ယခု tabs များတွင် ထိုဝန်ထမ်း၏ မှတ်တမ်းများကို read-only ဖတ်ရှုနိုင်သည်။ (Leave tab တွင် လက်ရှိနှစ်၏ balance chips များပါ ပါဝင်သည်။) Tab တစ်ခုစီသည် မိမိ role တွင် သက်ဆိုင်ရာ view permission ရှိမှသာ data ပြသည် — မရှိပါက "Your account does not include the … permission" empty state ပြမည်။

### ၉.၅ ဝန်ထမ်းစာရွက်စာတမ်းများ (Document Management)

**လိုအပ်ခွင့်ပြုချက်** — DOCUMENT_MANAGE

**Upload လုပ်ပုံ —**

1. Profile → **Documents** tab သို့ သွားပါ။
2. **Upload** နှိပ်ပါ။ File chooser တွင် PDF, Word, Excel, JPG, PNG file ရွေးပါ။
3. Document **Type** (ဥပမာ — NRC copy, Contract, Certificate) နှင့် **Notes** ဖြည့်ပါ။
4. Confirm လုပ်ပါ။

**အခြားလုပ်ဆောင်ချက်များ —**

- **Archive** — စာရွက်စာတမ်းကို archive လုပ်သည် (file ကို disk ပေါ်တွင် ဆက်ထား remains)
- **Delete** — Record ကို soft-delete လုပ်သည် (list မှ ပျောက်သည်)
- စာရွက်စာတမ်းသက်တမ်းကုန်ဆုံးရန် နီးစပ်ပါက (ပုံသေ ၃၀ ရက်အတွင်း) toolbar တွင် warning message ပေါ်မည်ဖြစ်ပြီး Notification လည်း ရရှိမည်ဖြစ်သည်။

---

## ၁၀။ Recruitment (ဝန်ထမ်းခေါ်ယူရေး)

**လိုအပ်ခွင့်ပြုချက်** — RECRUITMENT_VIEW / RECRUITMENT_MANAGE / INTERVIEW_MANAGE / OFFER_MANAGE

Recruitment module တွင် **Tab ၅ ခု** ပါဝင်ပြီး workflow တစ်ခုလုံးကို လိုက်နာပါ —

```
Vacancy (လစ်လပ်ရာထူး) → Candidate (လျှောက်ထားသူ) → Application (လျှောက်လွှာ)
→ Interview (တွေ့ဆုံမေးမြန်းခြင်း) → Offer (အလုပ်ချင်းပြုချက်) → Hire (ဝန်ထမ်းအဖြစ်ပြောင်း)
```

### ၁၀.၁ Vacancies Tab — လစ်လပ်ရာထူးဖွင့်ခြင်း

1. **New Vacancy** နှိပ်ပါ။
2. Job Title*, Department*, Position*, Headcount* (၁–၉၉၉), Employment Type* (FULL_TIME/PART_TIME/CONTRACT/INTERN/PROBATION), Salary Min/Max, Opening Date*, Closing Date, Job Description, Requirements များ ဖြည့်ပါ။
3. **Open Vacancy** နှိပ်ပါ။

Status များ — OPEN → ON_HOLD / FILLED / CLOSED / CANCELLED  
Context menu လုပ်ဆောင်ချက်များ — Edit (OPEN/ON_HOLD တွင်သာ), Put On Hold, Reopen, Mark Filled, Close, Cancel

### ၁၀.၂ Candidates Tab — လျှောက်ထားသူများ

1. **New Candidate** နှိပ်ပါ။
2. First Name*, Last Name*, Gender, Date of Birth, Phone*, Email, Address, Skills (comma ဖြင့်ခွဲရေးပါ), Experience (years), Expected Salary, Source* (WEBSITE/REFERRAL/AGENCY/LINKEDIN/JOB_FAIR/WALK_IN/OTHER) ဖြည့်ပါ။
3. **Choose Resume...** ဖြင့် Resume file (pdf/doc/docx/jpg/png) attach လုပ်နိုင်သည်။

Candidate Pipeline — NEW → SHORTLISTED → INTERVIEWING → OFFERED → HIRED  
Reject လုပ်လိုပါက context menu မှ **Reject** ရွေး၍ **အကြောင်းပြချက် ဖြည့်ရန် မဖြစ်မနေ လိုအပ်သည်** (ReasonDialog)။

### ၁၀.၃ Applications Tab — လျှောက်လွှာများ

1. **New Application** နှိပ်ပါ (Candidate နှင့် OPEN vacancy ရှိရန် လိုအပ်သည်)။
2. Candidate*, Vacancy (OPEN)*, Cover Letter ဖြည့်ပါ။

Application Flow —

| Stage | Context Menu Action |
|---|---|
| SUBMITTED | Shortlist (to Screening) |
| SCREENING | Schedule Interview |
| INTERVIEW | Create Offer / Schedule Interview |
| မည်သည့် active stage မဆို | Reject (အကြောင်းပြချက်ဖြင့်) / Withdraw |

### ၁၀.၄ Interviews Tab — တွေ့ဆုံမေးမြန်းခြင်း

**Interview Schedule လုပ်ပုံ —**

1. **Schedule Interview** နှိပ်ပါ။
2. Application*, Interview Date* (default — ဒီနေ့), Start Time (HH:mm)* (default — 10:00), Mode* (IN_PERSON/PHONE/VIDEO), Interviewer, Notes ဖြည့်ပါ။

**Result မှတ်ပုံ —**

- Interview ပြီးပါက row ပေါ် right-click → **Record Result...**
- Result* (PASS/FAIL/ON_HOLD), Score (0–100), Notes ဖြည့်ပါ။
- PASS ဖြစ်ပါက Application သည် INTERVIEW stage သို့ ရောက်သွားမည်ဖြစ်ပြီး Offer ဖန်တီးနိုင်ပါသည်။

### ၁၀.၅ Offers Tab — အလုပ်ချင်းပြုချက်

1. INTERVIEW stage ရှိ application မှ **Create Offer** နှိပ်ပါ။
2. Offered Salary *, Offer Date *, Expiry Date, Joining Date ဖြည့်၍ **Create Draft Offer** နှိပ်ပါ။

Offer Status Flow —

```
DRAFT → SENT → ACCEPTED → (Hire Candidate → ဝန်ထမ်း record အသစ် ဖန်တီးမည်)
              → DECLINED / EXPIRED / CANCELLED
```

- **Send Offer** — Draft ကို ပေးပို့ပြီးအဖြစ် ပြောင်းသည်
- **Accept Offer** — လျှောက်ထားသူ လက်ခံကြောင်း မှတ်သည်
- **Hire Candidate** — ACCEPTED offer တွင်သာ ရနိုင်ပြီး ဝန်ထမ်း record အသစ် အလိုအလျောက် ဖန်တီးပေးမည်ဖြစ်သည်

---

## ၁၁။ Onboarding (ဝန်ထမ်းအသစ် စတင်လုပ်ငန်းစဉ်)

**လိုအပ်ခွင့်ပြုချက်** — ONBOARDING_MANAGE

Onboarding module တွင် Tab ၂ ခု ရှိသည် —

### ၁၁.၁ Templates Tab — Checklist Template စီမံခြင်း

ဝန်ထမ်းအသစ်တိုင်း လိုက်နာရမည့် လုပ်ငန်းစဉ် (checklist item) များကို သတ်မှတ်သည်။ ပုံသေ template ၁၀ ခု ပါဝင်ပြီး အောက်ပါအတိုင်း စီစဉ်ထားသည် —

1. Create employee profile
2. Sign employment contract
3. Collect national ID copy
4. Collect other documents
5. Assign department & position
6. Set up salary structure
7. Assign shift
8. Issue company assets
9. Attend orientation
10. Create system account

**Template အသစ်ထည့်ပုံ —** **New Template** နှိပ် → Task Name*, Description, Order* (၁–၉၉၉), Mandatory task ✓, Active ✓ ဖြည့်၍ Save ပါ။

> Template ပြောင်းလဲမှုများသည် **အသစ်ဖန်တီးမည့် checklist များအတွက်သာ** သက်ရောက်ပြီး ရှိပြီးသား checklist များကို မပြောင်းလဲပါ။

### ၁၁.၂ Checklists Tab — Checklist ဖန်တီးခြင်းနှင့် လိုက်နာခြင်း

1. Toolbar ရှိ **Employee:** combo မှ Active ဝန်ထမ်း ရွေးပါ။
2. **Generate Checklist** button နှိပ်ပါ (Checklist မရှိသေးမှသာ ပေါ်မည်)။ Task များ၏ Due date သည် ဒီနေ့မှ ရက်အနည်းငယ်အတွင်း အလိုအလျောက် တွက်ပေးမည်ဖြစ်သည်။
3. Progress bar တွင် `"N/M done"` နှင့် mandatory ကျန်ရှိမှု ပြသည်။

**Task တစ်ခုချင်းစီ၏ လုပ်ဆောင်ချက်များ (Right-click)** —

| Action | ရှင်းလင်းချက် |
|---|---|
| Mark Completed | ပြီးမြောက်ကြောင်း မှတ်သည် |
| Skip | ကျော်လွှားသည် |
| Waive | မလိုအပ်ကြောင်း လွတ်ငြိမ်းချမ်းသာပေးသည် |
| Reopen | PENDING သို့ ပြန်ဖွင့်သည် |

Task Status — PENDING / COMPLETED / SKIPPED / WAIVED

---

## ၁၂။ Attendance (အလုပ်တက်မှု မှတ်တမ်း)

**လိုအပ်ခွင့်ပြုချက်** — ATTENDANCE_VIEW / ATTENDANCE_CREATE / ATTENDANCE_UPDATE

### ၁၂.၁ မှတ်တမ်းကြည့်ခြင်း

- **Date** picker (default — ဒီနေ့) ဖြင့် ရက်စောင့်ကြည့်နိုင်သည်။
- Columns — Code, Employee, Department, In, Out, Status, Late, Early, Worked, OT
- Status filter — PRESENT / LATE / EARLY_LEAVE / HALF_DAY / ABSENT / WEEKEND / MISSION

### ၁၂.၂ Check In / Check Out လုပ်ပုံ

**Check In (အလုပ်တက်) —**

1. ဝန်ထမ်း row ကို ရွေးပါ။
2. **Check In** button (အစိမ်းရောင်) နှိပ်ပါ။

**Check Out (အလုပ်ဆင်း) —**

1. Check In ပြီးသား (In time ရှိပြီး Out time မရှိသေးသော) row ကို ရွေးပါ။
2. **Check Out** button နှိပ်ပါ။

Late/Early/Worked/OT စာရင်းများကို စနစ်က အလိုအလျောက် တွက်ပေးမည်ဖြစ်သည်။

### ၁၂.၃ Mark Absentees — ချွတ်ယွင်းသူများ အလိုအလျောက် မှတ်ခြင်း

**Mark Absentees** button နှိပ်ပါက ရွေးထားသောရက်အတွက် record မရှိသေးသော active ဝန်ထမ်းများအတွက် attendance row များ အလိုအလျောက် ဖန်တီးပေးမည်ဖြစ်သည် ("N attendance row(s) generated" toast ပြမည်)။

### ၁၂.၄ Correction (ပြင်ဆင်ခြင်း)

1. Row ကို right-click (သို့) Double-click → **Correct...**
2. **Check In (HH:mm)** \* နှင့် **Check Out (HH:mm)** \* ကို မှန်ကန်သောအချိန်ဖြင့် ပြင်ပါ။
3. **Reason** \* — ပြင်ဆင်ရခြင်း အကြောင်းပြချက် **မဖြစ်မနေ ဖြည့်ရမည်** (မှတ်တမ်းတင်မည်ဖြစ်သည်)။
4. **Apply Correction** နှိပ်ပါ။

### ၁၂.၅ Monthly View — လစဉ်မှတ်တမ်း

Row ပေါ် right-click → **Monthly View** ရွေးပါ။

- Month/Year ရွေး၍ ဝန်ထမ်းတစ်ဦး၏ တစ်လတာ မှတ်တမ်းကို ရက်ရက်လိုက် ကြည့်နိုင်သည်။
- Footer တွင် စုစုပေါင်း — Present, Late, Early, Half-day, Absent, Worked hours, Overtime hours ပြသည်။

---

## ၁၃။ Shifts (အလုပ်ချိန်ဇယားများ)

**လိုအပ်ခွင့်ပြုချက်** — SHIFT_VIEW / SHIFT_MANAGE / SHIFT_ASSIGN

Shift module တွင် Tab ၂ ခု ရှိသည် —

### ၁၃.၁ Shifts Tab — Shift အမျိုးအစားများ

ပုံသေ Shift ၄ မျိုး ပါဝင်သည် —

| Code | အမည် | အချိန် |
|---|---|---|
| SH-MORNING | Morning Shift | 08:00 – 17:00 |
| SH-EVENING | Evening Shift | 16:00 – 00:00 (overnight) |
| SH-NIGHT | Night Shift | 23:00 – 07:00 (overnight) |
| SH-FLEX | Flexible Hours | 09:00 – 18:00 |

**Shift အသစ် ဖန်တီးပုံ —** **New Shift** နှိပ် → Code*, Shift Name*, Start Time (HH:mm)*, End Time (HH:mm)*, Grace Minutes (default 10), Break Minutes (default 60), Description ဖြည့်ပါ။ Overnight shift (end ≤ start) လည်း ခွင့်ပြုသည်။

### ၁၃.၂ Assignments Tab — ဝန်ထမ်းများကို Shift ပေးအပ်ခြင်း

**Shift တစ်ခု ပေးအပ်ပုံ —**

1. **Assign Shift** နှိပ်ပါ။
2. Employee* (Active ဝန်ထမ်း), Shift*, Effective From* (default — ဒီနေ့) ရွေးပါ။
3. **Assign** နှိပ်ပါ။

> ဝန်ထမ်းတစ်ဦးတွင် လက်ရှိ shift တစ်ခုသာ ရှိရမည်ဖြစ်ပြီး **assignment history အားလုံးကို စနစ်က သိမ်းဆည်းထားသည်။**

Assignment လုပ်ဆောင်ချက်များ (Right-click) —

- **History** — ယခင် assignment များ ကြည့်ခြင်း
- **End Assignment** — လက်ရှိ assignment ကို ဒီနေ့အထိ အဆုံးသတ်ခြင်း
- **Reassign...** — အခြား shift သို့ ပြောင်းခြင်း

---

## ၁၄။ Leave (အားလပ်ရက် တောင်းခံခြင်း)

**လိုအပ်ခွင့်ပြုချက်** — LEAVE_VIEW / LEAVE_REQUEST / LEAVE_APPROVE / LEAVE_CANCEL

### ၁၄.၁ အားလပ်ရက် အမျိုးအစားများ (ပုံသေ)

| Type | နှစ်စဉ်ခွင့်ရက် | လစာပါ/မပါ | မှတ်ချက် |
|---|---|---|---|
| Annual Leave | ၁၈ ရက် | Paid | နှစ်ပြောင်းရင် အသုံးမပြုရသေးသည့် ၅ ရက်အထိ ရွှေ့နိုင် |
| Sick Leave | ၁၄ ရက် | Paid | — |
| Casual Leave | ၇ ရက် | Paid | — |
| Maternity Leave | ၉၀ ရက် | Paid | အမျိုးသမီးများအတွက်သာ |
| Paternity Leave | ၁၅ ရက် | Paid | အမျိုးသားများအတွက်သာ |
| Unpaid Leave | ၃၀ ရက် | Unpaid | — |
| Other Leave | ၅ ရက် | Unpaid | — |

### ၁၄.၂ အားလပ်ရက် Request တင်ပုံ

1. **Leave** module → Requests tab → **New Request** နှိပ်ပါ။
2. Employee*, Leave Type*, Start Date*, End Date*, Reason* (မဖြစ်မနေ) ဖြည့်ပါ။
3. Form အောက်တွင် **Available: N day(s)** ဟု လက်ရှိ balance အလိုအလျောက် ပြမည်ဖြစ်သည်။
4. **Submit Request** နှိပ်ပါ → Status သည် **PENDING** ဖြစ်သွားမည်။

### ၁၄.၃ Approval Workflow — အဆင့် ၂ ဆင့်

PENDING request ပေါ် Right-click →

1. **Approve (Manager)** — ဌာနမှူးအဆင့် အတည်ပြုခြင်း
2. **Approve (HR – Final)** — HR နောက်ဆုံးအဆင့် အတည်ပြုခြင်း
3. **Reject** — ပယ်ဖျက်ခြင်း
4. **Cancel Request** — PENDING ဖြစ်နေစဉ် ရုပ်သိမ်းခြင်း

> Approval အပြီးတွင် ဝန်ထမ်း၏ leave balance မှ ရက်များ အလိုအလျောက် နုတ်ယူမည်ဖြစ်သည်။ Requests tab နှင့် Balances tab နှစ်ခုစလုံး ပြန်လည် refresh ဖြစ်သည်။

### ၁၄.၄ Balances Tab — Balance ကြည့်ခြင်း

Employee နှင့် Year ရွေး၍ ဝန်ထမ်းတစ်ဦး၏ အားလပ်ရက် balance အားလုံးကို ကြည့်နိုင်သည် —

Type · Entitled (ခွင့်ပြုရက်) · Carried (ရွှေ့လိုက်ရက်) · Used (အသုံးပြုပြီး) · Pending (စောင့်ဆိုင်း) · Adjusted · Available (ကျန်ရှိ)

---

## ၁၅။ Overtime (အလုပ်ပိုချိန်)

**လိုအပ်ခွင့်ပြုချက်** — OVERTIME_VIEW / OVERTIME_REQUEST / OVERTIME_APPROVE

### ၁၅.၁ Overtime Request တင်ပုံ

1. **Overtime** module → **Request Overtime** နှိပ်ပါ။
2. Employee*, Overtime Date* (default — ဒီနေ့), Hours*, Reason* ဖြည့်ပါ။
3. **Hours သည် 0.01 – 12 အတွင်းသာ ဖြစ်ရမည်** (ထို့ထက်ပိုပါက error ပြမည်)။
4. **Submit Request** နှိပ်ပါ → Status **PENDING** ဖြစ်သွားမည်။

### ၁၅.၂ Approval

Approve လုပ်သည့်အခါ ဝန်ထမ်း၏ hourly rate နှင့် amount ကို အချိန်နှင့်တပြေးညီ snapshot ရိုက်သည်။ ထို့ကြောင့် Rate/h နှင့် Amount columns များသည် Approve မလုပ်မချင်း `-` အဖြစ် ပြမည်ဖြစ်သည်။

Context menu — **Approve** / **Reject** (PENDING ဖြစ်နေစဉ်သာ)

Overtime rate ကို App Settings တွင် `payroll.overtime_rate_multiplier = 1.5` ဟု သတ်မှတ်ထားသည်။

---

## ၁၆။ Payroll (လစာတွက်ချက်ခြင်း)

**လိုအပ်ခွင့်ပြုချက်** — PAYROLL_VIEW / PAYROLL_CALCULATE / PAYROLL_REVIEW / PAYROLL_APPROVE / PAYROLL_MARK_PAID / PAYSLIP_GENERATE

### ၁၆.၁ Payroll Status Flow

```
DRAFT → CALCULATED → REVIEWED → APPROVED → PAID
                                  ↘ CANCELLED
```

### ၁၆.၂ လစာတွက်ချက်ခြင်း အဆင့်ဆင့်

Payroll module ကို ပထမဆုံးဖွင့်ပါက လက်ရှိလအတွက် payroll period ကို အလိုအလျောက် ဖန်တီးပေးသည်။

1. Toolbar ရှိ **Period:** combo မှ လကာလ ရွေးပါ (ဥပမာ — `2026-08 (DRAFT)`)။
2. **Calculate** နှိပ်ပါ → ဝန်ထမ်းတိုင်းအတွက် payroll record များ တွက်ပေးမည် (Status → CALCULATED)။
3. **Review All** နှိပ်ပါ → CALCULATED များကို REVIEWED ဖြစ်စေသည်။
4. **Approve All** နှိပ်ပါ → REVIEWED များကို APPROVED ဖြစ်စေသည်။
5. **Mark Paid** နှိပ်ပါ → APPROVED များကို PAID ဖြစ်စေသည်။

> Row တစ်ခုချင်းစီကိုလည်း Right-click ဖြင့် **Mark Reviewed / Approve / Mark Paid** တစ်ဦးချင်း လုပ်ဆောင်နိုင်သည်။

### ၁၆.၃ လစာပြစာ (Payslip) ထုတ်ယူခြင်း

1. APPROVED သို့မဟုတ် PAID ဖြစ်သော payroll row ကို Right-click လုပ်ပါ။
2. **Download Payslip** ရွေးပါ။
3. Payslip PDF သည် **Desktop** ပေါ်တွင် အလိုအလျောက် သိမ်းသွားမည်ဖြစ်ပြီး success toast ပြမည် ("Payslip saved to Desktop: ...")။

### ၁၆.၄ ပုံသေ Payroll Settings

| Setting | တန်ဖိုး |
|---|---|
| Currency | USD |
| Overtime Rate Multiplier | 1.5× |
| Income Tax | 5% |
| Social Security (Employee) | 2% |
| Social Security (Employer) | 3% |
| Working Days/Month | 22 ရက် |

Allowance များ (Transport, Housing စသည်) ကို ဝန်ထမ်းအလိုက် recurring အဖြစ် သတ်မှတ်ထားနိုင်သည်။

---

## ၁၇။ Performance (စွမ်းဆောင်ရည် သုံးသပ်ခြင်း)

**လိုအပ်ခွင့်ပြုချက်** — PERFORMANCE_VIEW / PERFORMANCE_MANAGE

### ၁၇.၁ Criteria Tab — သုံးသပ်ချက် သတ်မှတ်ခြင်း

ပုံသေ criteria ၇ ခု (weight ပေါင်း 100%) —

Productivity (20%) · Quality of Work (20%) · Communication (10%) · Teamwork (10%) · Attendance (10%) · Leadership (10%) · Technical Skills (20%)

Toolbar တွင် "Active weight total: N%" ပြသည် — 100% မဖြစ်ပါက warning ပေါ်မည်ဖြစ်သည်။ **New Criterion** ဖြင့် criteria အသစ် ထည့်နိုင်သည် (Code*, Name*, Weight %*)။

### ၁၇.၂ Reviews Tab — Review လုပ်ခြင်း အဆင့်ဆင့်

**Stage flow** — MANAGER_REVIEW → EMPLOYEE_FEEDBACK → FINALIZED

1. **New Review** နှိပ်ပါ → Employee*, Reviewer (optional), Period Start*, Period End*, Manager Comments ဖြည့်၍ **Create Draft** နှိပ်ပါ။
2. Right-click → **Score Criteria** — Criterion တစ်ခုချင်းစီအတွက် Score (1–5, step 0.5, default 3.0) နှင့် Comments ဖြည့်၍ **Save Scores** နှိပ်ပါ။
3. Right-click → **Submit for Feedback** — ဝန်ထမ်း feedback ရယူရန် ပေးပို့ပါ။
4. Right-click → **Record Feedback...** — ဝန်ထမ်း၏ comments* ရိုက်၍ **Submit Feedback** နှိပ်ပါ။
5. Right-click → **Finalize** — Weighted overall score (X / 5) ကို အလိုအလျောက် တွက်ပြီး review ကို **အပြီးအစီး lock** လုပ်သည် (ထပ်မံ ပြင်ဆင်လို့ မရတော့ပါ)။

> **Cancel Review** ကို DRAFT / IN_PROGRESS အဆင့်တွင်သာ လုပ်နိုင်သည်။

---

## ၁၈။ Training (သင်တန်းများ)

**လိုအပ်ခွင့်ပြုချက်** — TRAINING_VIEW / TRAINING_MANAGE

Training module တွင် Tab ၃ ခု ရှိသည် —

### ၁၈.၁ Programs Tab — သင်တန်း ဖွင့်ခြင်း

**New Program** → Program Name*, Trainer, Cost, Capacity (blank = unlimited ∞), Description ဖြည့်ပါ။

Program Status — PLANNED → ONGOING → COMPLETED (သို့) CANCELLED  
Context menu — Start (ONGOING), Complete, Cancel Program, Edit (PLANNED/ONGOING တွင်သာ)

### ၁၈.၂ Sessions Tab — သင်တန်းရက်များ

**New Session** → Start Date*/Start Time* (default 09:00), End Date*/End Time* (default 17:00), Location ဖြည့်ပါ။ Duration (hour(s)) ကို အလိုအလျောက် တွက်ပြပေးမည်ဖြစ်သည်။

Session Status — SCHEDULED → ONGOING → COMPLETED (သို့) CANCELLED

> Session ဖန်တီးရန် Program filter တွင် သင်တန်းတစ်ခု ရွေးထားရမည်။

### ၁၈.၃ Enrollments Tab — ဝန်ထမ်း တက်ရောက်မှု

1. **Enroll Employee** နှိပ်ပါ (သင်တန်းသည် PLANNED သို့မဟုတ် ONGOING ဖြစ်ရမည်)။
2. Employee*, Session (optional — "Not pinned to a session" ရွေးနိုင်) ဖြည့်ပါ။ Capacity/seats taken ကို header တွင် ပြသည်။

**Result မှတ်ခြင်း** — Right-click → **Record Result...** → Outcome (ENROLLED/ATTENDED/COMPLETED/PASSED/FAILED/NO_SHOW), Score (0–100), Notes ဖြည့်ပါ။

> Terminal outcome (COMPLETED/PASSED/FAILED/NO_SHOW) မှတ်ပြီးပါက record ကို **lock** လုပ်သည် — ပြင်ဆင်လို့ မရတော့ပါ။ ENROLLED ဖြစ်နေစဉ် **Unenroll** လည်း လုပ်နိုင်သည်။

---

## ၁၉။ Assets (ကုမ္ပဏီပစ္စည်းများ)

**လိုအပ်ခွင့်ပြုချက်** — ASSET_VIEW / ASSET_ASSIGN / ASSET_MANAGE

### ၁၉.၁ Assets Tab — ပစ္စည်း မှတ်ပုံတင်ခြင်း

**New Asset** → Asset Name*, Category* (LAPTOP/DESKTOP/MONITOR/PHONE/TABLET/ID_CARD/VEHICLE/FURNITURE/OTHER), Serial Number, Purchase Date, Purchase Cost, Warranty Expiry, Condition* (NEW/GOOD/FAIR/POOR/DAMAGED), Notes ဖြည့်ပါ။

Asset Status — AVAILABLE → ASSIGNED / UNDER_REPAIR / RETIRED / LOST  
(RETIRED နှင့် LOST သည် terminal status များဖြစ်ပြီး ပြန်ပြင်လို့ မရပါ)

Context menu — Assign to Employee..., Send to Repair, Mark Available, Retire, Mark Lost

### ၁၉.၂ ပစ္စည်းချေးပေးခြင်း (Assign)

1. AVAILABLE ဖြစ်သော asset ပေါ် Right-click → **Assign to Employee...**
2. Employee* (Active ဝန်ထမ်း), Assigned Date* (default — ဒီနေ့), Due Return Date (optional), Notes ဖြည့်ပါ။
3. **Assign Asset** နှိပ်ပါ → asset status သည် ASSIGNED ဖြစ်သွားမည်။

Due Return Date ကျော်လွန်နေသော assignment များကို စနစ်က **OVERDUE** flag တပ်ပြမည်ဖြစ်သည်။

### ၁၉.၃ Assignments Tab — ပြန်အပ်ခြင်း (Return)

1. Open assignment ပေါ် Right-click → **Return Asset...**
2. Returned Date*, Condition on Return* (GOOD/FAIR/POOR/DAMAGED), Notes (အကြောင်း 500 စာလုံးအတွင်း) ဖြည့်ပါ။
3. **Confirm Return** နှိပ်ပါ။
   - Condition = DAMAGED ဖြစ်ပါက asset သည် **UNDER_REPAIR** သို့ သွားမည်။
   - အခြား condition များဆိုပါက **AVAILABLE** ပြန်ဖြစ်သွားမည်။

> **Mark Lost** — ပစ္စည်းပျောက်ဆုံးကြောင်း မှတ်တမ်းတင်သည် (asset လည်း LOST ဖြစ်သွားမည်)။

---

## ၂၀။ Separation (ဝန်ထမ်း နုတ်ထွက်/ဖယ်ရှားခြင်း)

**လိုအပ်ခွင့်ပြုချက်** — SEPARATION_MANAGE

### ၂၀.၁ Resignations Tab — နုတ်ထွက်လွှာ

**နုတ်ထွက်လွှာ တင်ပုံ —**

1. **New Resignation** နှိပ်ပါ။
2. Employee*, Resignation Date* (default — ဒီနေ့), Last Working Date* (default — ယနေ့မှ ၃၀ ရက်အတွင်း), Reason ဖြည့်ပါ။
3. Form တွင် **Notice period: N day(s)** ကို အလိုအလျောက် တွက်ပြမည်။ (Last Working Date သည် Resignation Date ထက် စောနေပါက warning ပြမည်)
4. **Submit Resignation** နှိပ်ပါ → Status **SUBMITTED**။

**Resignation Status Flow —**

```
SUBMITTED → APPROVED → PROCESSED (Exit Checklist)
          → REJECTED / WITHDRAWN
```

**Exit Checklist Process** — **Process Exit Checklist...** နှိပ်ပါက တစ်ပြိုင်နက်တည်း —

- ဝန်ထမ်း status ကို **RESIGNED** ပြောင်းသည်
- Open shift assignments များကို ပိတ်သည်
- ချေးထားသော ကုမ္ပဏီပစ္စည်းများကို ပြန်ရရှိကြောင်း မှတ်သည်
- Draft payroll များကို void လုပ်သည်

ထို့အပြင် **Record Exit Interview...** ဖြင့် exit interview မှတ်ချက် ထည့်နိုင်သည်။

### ၂၀.၂ Terminations Tab — အလုပ်မှ ဖယ်ရှားခြင်း

⚠️ **သတိ** — Termination သည် **ချက်ချင်း အကျိုးသက်ရောက်မည်** ဖြစ်သည် — ဝန်ထမ်းကို TERMINATED ပြောင်း၊ assignments ပိတ်၊ assets ပြန်ရမည်။ Button သည် danger (အနီ) အရောင်ဖြစ်ပြီး **double confirmation** မေးမည်။

Fields — Employee*, Termination Date* (default — ဒီနေ့), Reason Category* (MISCONDUCT/PERFORMANCE/LAYOFF/CONTRACT_END/OTHER), Reason, Notes, Eligible for rehire ✓

> Termination record များသည် မှတ်ပြီးပါက **read-only** ဖြစ်သည် — ပြင်ဆင်လို့ မရပါ။

---

## ၂၁။ Reports (အစီရင်ခံစာများ)

**လိုအပ်ခွင့်ပြုချက်** — REPORT_VIEW (ကြည့်ရန်), REPORT_EXPORT (Export/Print လုပ်ရန်)

### ၂၁.၁ Report Catalog — အစီရင်ခံစာ ၁၄ မျိုး

| # | Report | ရှင်းလင်းချက် |
|---|---|---|
| 1 | Employee List | ဝန်ထမ်းစာရင်း (status filter ဖြင့်) |
| 2 | Department Report | ဌာနအလိုက် အချက်အလက် |
| 3 | Attendance Report | အလုပ်တက်မှု အစီရင်ခံစာ |
| 4 | Late Report | နောက်ကျမှု အစီရင်ခံစာ |
| 5 | Absence Report | ချွတ်ယွင်းမှု အစီရင်ခံစာ |
| 6 | Leave Report | အားလပ်ရက် တောင်းခံမှုများ |
| 7 | Leave Balance | အားလပ်ရက် balance များ |
| 8 | Overtime Report | အလုပ်ပိုချိန် အစီရင်ခံစာ |
| 9 | Payroll Report | လစာစာရင်း |
| 10 | Salary Report | လစာဖွဲ့စည်းပုံ |
| 11 | Performance Report | စွမ်းဆောင်ရည် ရမှတ်များ |
| 12 | Training Report | သင်တန်း တက်ရောက်မှု |
| 13 | Asset Report | ပစ္စည်းစာရင်း |
| 14 | Employee Turnover Report | ဝန်ထမ်းဝင်ထွက်မှု |

### ၂၁.၂ Report ထုတ်ယူပုံ

1. ဘယ်ဘက် **REPORT CATALOG** list မှ report တစ်ခု ရွေးပါ။
2. Filter များ သတ်မှတ်ပါ —
   - **From / To** dates (default — လ၏ ပထမရက်မှ ဒီနေ့အထိ)
   - **Department** (All Departments)
   - **Status** (report အလိုက်)
   - **Keyword search** ("Search name or code...")
3. **Generate** button နှိပ်ပါ ("Generating..." ဟု ပြမည်)။
4. Preview table တွင် ရလဒ်များ ပေါ်လာမည်ဖြစ်သည် (totals row ပါဝင်သော report များလည်း ရှိသည်)။

### ၂၁.၃ Export လုပ်ပုံ

Data generate ပြီးပါက toolbar မှ —

- **Export PDF** — `.pdf` file အဖြစ် save (save dialog ဖြင့် နေရာရွေး)
- **Export Excel** — `.xlsx` file အဖြစ် save
- **Print** — Native print dialog ဖြင့် ပရင့်ထုတ်ခြင်း

> Data generate မလုပ်ရသေးဘဲ export လုပ်ပါက "Generate a report first." warning ပြမည်။

---

## ၂၂။ Audit Log (စစ်ဆေးမှု မှတ်တမ်း)

**လိုအပ်ခွင့်ပြုချက်** — AUDIT_LOG_VIEW

စနစ်ထဲမှ လုပ်ဆောင်ချက် **အားလုံး** (ဘယ်သူ၊ ဘယ်အချိန်၊ ဘာလုပ်ခဲ့သလဲ) ကို append-only မှတ်တမ်းအဖြစ် သိမ်းဆည်းသည် — မည်သူမှ မပြင်ဆင်နိုင်ပါ။

### Filter လုပ်ပုံ

- **Search** — description, entity, action, user ဖြင့် ရှာခြင်း
- **Module / Action / User** combos ဖြင့် စစ်ခြင်း
- **Date range** — default သည် လွန်ခဲ့သော ၃၀ ရက်မှ ဒီနေ့အထိ

### Columns

When (အချိန်) · User · Action (badge) · Module · Entity · Entity ID · Description · IP

**Detail ကြည့်ရန်** — Row ပေါ် Double-click နှိပ်ပါ။ "Audit Entry #id" dialog တွင် Device information အပါအဝင် အသေးစိတ် ပြမည်။

---

## ၂၃။ Notifications (အသိပေးကြောင်းများ)

Header ပေါ်ရှိ **🔔 Bell icon** ကို နှိပ်ပါက Notification Center dialog ဖွင့်မည်ဖြစ်သည်။

### အသိပေးကြောင်း အမျိုးအစားများ

- Approval များ (leave/overtime စသည်)
- Document expiry warnings (သက်တမ်းကုန်မည့် စာရွက်စာတမ်းများ)
- မွေးနေ့ အထိမ်းအမှတ်များ (Birthdays)
- သင်တန်း reminder များ
- အခြား system alerts

### အသုံးပြုပုံ

- **Feed filter** — All / Unread
- မဖတ်ရသေးသော row များကို **Bold** ဖြင့် ပြသည်
- **Mark as Read** — ရွေးထားသော row ကို ဖတ်ပြီးကြောင်း မှတ်ခြင်း (သို့) Row ပေါ် **Double-click**
- **Mark All Read** — အားလုံးကို တစ်ပြိုင်နက် ဖတ်ပြီးကြောင်း မှတ်ခြင်း
- Bell badge သည် unread အရေအတွက်ကို ၆၀ စက္ကန့်တစ်ကြိမ် update ပြုလုပ်သည်

---

## ၂၄။ အသုံးပြုသူအခန်းကဏ္ဍများနှင့် ခွင့်ပြုချက်များ (Roles & Permissions)

စနစ်တွင် Role ၆ မျိုး ပါဝင်ပြီး Sidebar menu များသည် မိမိ၏ role ခွင့်ပြုချက်အပေါ် မူတည်၍ ပေါ်လာမည်ဖြစ်သည်။

| Role | အဓိက တာဝန်ဝတ္တရား |
|---|---|
| **Super Administrator** | စနစ်တစ်ခုလုံးကို အပြည့်အဝ သုံးခွင့်ရှိသည် (settings + user management အပါအဝင်) |
| **HR Manager** | HR လုပ်ငန်းစဉ် အားလုံး (system administration မှလွဲ၍) |
| **HR Officer** | နေ့စဉ် HR လုပ်ငန်း — ဝန်ထမ်း records, attendance, leave, recruitment |
| **Department Manager** | Team leave/overtime approval, performance review ကျင်းပခြင်း |
| **Finance** | Payroll calculate/review/approve/mark paid, financial reports |
| **Employee** | Self-service — မိမိ attendance, leave request, payslip, training ကြည့်ခြင်း |

> ခွင့်ပြုချက် မရှိသော module ကို နှိပ်ပါက "Access denied" empty-state panel ပေါ်မည်ဖြစ်သည်။ ခွင့်ပြုချက်ရှိသော button များသာ မြင်ရမည်ဖြစ်သည်။

---

## ၂၅။ အသုံးဝင်သတိပြုချက်များ (Tips)

| Tip | ရှင်းလင်းချက် |
|---|---|
| `F5` | လက်ရှိ module refresh |
| Right-click | Table rows တွင် context menu လုပ်ဆောင်ချက်များ |
| Double-click | Row ဖွင့်ခြင်း (profile/detail dialogs) |
| Confirmation dialogs | အရေးကြီးသော action များ (Deactivate, Approve, Delete...) မလုပ်မီ အတည်ပြုချက် အမြဲမေးသည် |
| Toast messages | လုပ်ဆောင်ချက် အောင်မြင်/မအောင်မြင်ကို အချိန်ကာလအတိုအတွင်း ပြသည် |
| Required field (*) | `*` ပါသော field များကို မဖြစ်မနေ ဖြည့်ရမည် |
| Theme toggle | Header ရှိ 🌙/☀️ icon ဖြင့် Light/Dark ရွှေ့နိုင်သည် |
| Logs | `logs/` folder တွင် application log files များ ရှိသည် |

---

## ၂၆။ ပြဿနာဖြေရှင်းခြင်း (Troubleshooting)

| ပြဿနာ | ဖြေရှင်းနည်း |
|---|---|
| `[STARTUP FAILED]` — Database error | MySQL server စတင်ထားမှု၊ `application.properties` ထဲ의 db.url / username / password များကို စစ်ပါ။ Environment variables (HRMS_DB_*) ကိုလည်း စစ်ပါ။ |
| "Too many failed attempts..." | ၃၀ စက္ကန့်အတွင်း ၅ ကြိမ် မှားခဲ့သဖြင့် account ခေတ္တပိတ်ထားခြင်းဖြစ်သည် — စက္ကန့်ပေါင်းများစွာ စောင့်ပါ။ |
| "Access denied" panel | မိမိ၏ role တွင် ယင်း module အတွက် ခွင့်ပြုချက် မရှိပါ — Super Administrator ကို ဆက်သွယ်ပါ။ |
| Dashboard data မပေါ် | Refresh icon (🔄) နှိပ်ပါ၊ သို့မဟုတ် `F5` နှိပ်ပါ။ Data မရှိသေးပါက empty-state message ပြမည်။ |
| Payslip download မရ | Payroll record သည် APPROVED သို့မဟုတ် PAID ဖြစ်မှသာ Download ရနိုင်သည်။ |
| Export buttons disabled | Report ကို ဦးစွာ Generate လုပ်ပါ။ REPORT_EXPORT ခွင့်ပြုချက်လည်း လိုအပ်သည်။ |
| Program ကို ပြန်ဖွင့်ရန် လိုအပ်ပါက | Logout → Login ပြန်ဝင်ပါ၊ သို့မဟုတ် application ကို ပိတ်၍ ပြန်ဖွင့်ပါ။ |
| အခြားပြဿနာများ | `logs/` folder ရှိ log file နောက်ဆုံးပိုင်း entries များကို စစ်ဆေး၍ IT support ကို ဆက်သွယ်ပါ။ |

---

*© 2026 AMS — HR Management System v1.0.0 · ဤလမ်းညွှန်စာတမ်းကို အသုံးပြုသူများအတွက် ရေးသားထားပါသည်။*
