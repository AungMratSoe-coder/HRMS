# HR Management System — Theme System (Theme စနစ် လည်ပတ်ပုံ ရှင်းလင်းချက်)

**Version:** 1.0.0
**Application Type:** Desktop Application (Java Swing + MySQL)
**UI Library:** FlatLaf 3.5.4

---

## 1. အနှစ်ချုပ် (Overview)

Application တွင် **Light** နှင့် **Dark** ဟူသော theme နှစ်မျိုး ပါရှိပြီး FlatLaf library ပေါ်တွင် အခြေခံထားသည်။ Header ရှိ sun/moon ခလုတ်ဖြင့် အလွယ်တကူ ပြောင်းလဲနိုင်ပြီး ပြောင်းလဲသည့်အခါ window အားလုံးတွင် **crossfade animation** ဖြင့် ချောမွေ့စွာ ကူးပြောင်းသည်။

Theme စနစ်ကို အလွှာ ၃ ခွဲ၍ တည်ဆောက်ထားသည် -

| အလွှာ | ဖိုင် | တာဝန် |
|---|---|---|
| **FlatLaf Properties** | `resources/flatlaf/*.properties` | Standard Swing widget များ (Button, TextField, Table, ScrollBar...) ၏ UI defaults |
| **Palette** | `src/main/java/com/ams/hrms/ui/theme/Palette.java` | Custom-paint component များအတွက် Role အလိုက် အရောင်ဖြန့်ဝေခြင်း |
| **ThemeManager** | `src/main/java/com/ams/hrms/ui/theme/ThemeManager.java` | Look & Feel တပ်ဆင်ခြင်း၊ theme ပြောင်းခြင်း၊ window အားလုံး refresh လုပ်ခြင်း၊ crossfade၊ listener စနစ် |

---

## 2. အဓိက ဖိုင်များ (Key Files)

| ဖိုင် | တာဝန် |
|---|---|
| `ui/theme/ThemeManager.java` | Theme စနစ်၏ ဗဟိုဌာနခွဲ — install / switch / crossfade / listeners |
| `ui/theme/Palette.java` | Semantic Role များအတိုင်း အရောင်ပြန်လည်ရှာဖွေပေးခြင်း |
| `resources/flatlaf/FlatLightLaf.properties` | Light theme အတွက် FlatLaf customization |
| `resources/flatlaf/FlatDarkLaf.properties` | Dark theme အတွက် FlatLaf customization |
| `util/IconLoader.java` | SVG icon cache နှင့် theme အလိုက် အရောင်ပြောင်းခြင်း |
| `ui/dashboard/ChartTheme.java` | JFreeChart များကို theme နှင့် ကိုက်ညီအောင် အရောင်ဆင်ခြင်း |
| `component/HeaderPanel.java` | Sun/Moon toggle ခလုတ် (theme ပြောင်းရန် entry point) |
| `config/Bootstrapper.java` | Application စတင်စဉ် `ThemeManager.install()` ခေါ်ဆောင်ခြင်း |

---

## 3. Application စတင်ခြင်း အဆင့်ဆင့် (Startup Flow)

```
Bootstrapper.main()
    └── ThemeManager.install()
          ├── FlatLaf.registerCustomDefaultsSource("flatlaf")   ← properties များ မှတ်ပုံတင်
          ├── applyTheme(LIGHT)                                  ← default theme
          │     ├── UIManager.setLookAndFeel(new FlatLightLaf())
          │     └── applyBaseDefaults()                          ← font / border overrides
          └── installed = true                                   ← ထပ်ခေါ်ပါက ချန်လှန် (idempotent)
```

**`install()` အတွင်း ဖြစ်ပျက်ပုံ**

1. `FlatLaf.registerCustomDefaultsSource("flatlaf")` — `resources/flatlaf/` folder ကို မှတ်ပုံတင်သည်။ FlatLaf သည် LAF class နာမည်နှင့် **တူညီသော** properties ဖိုင်ကို အလိုအလျောက် load သည် -
   - `FlatLightLaf` သုံးလျှင် → `FlatLightLaf.properties`
   - `FlatDarkLaf` သုံးလျှင် → `FlatDarkLaf.properties`
2. Default theme သည် **LIGHT** ဖြစ်သည် (`current = Theme.LIGHT`)။
3. `applyBaseDefaults()` မှ -
   - `defaultFont` — Windows တွင် **Segoe UI 13px**၊ မရှိပါက Dialog font (platform အလိုက် တစ်ကြိမ်သာ ရွေးသည်)
   - `ScrollPane.border` / `Table.scrollPaneBorder` — Palette မှ blend လုပ်ထားသော hairline border (focus ရှိလျှင် အပြာရောင် border ပေါ်လာခြင်း ဖယ်ရှားရန်)
   - `Table.focusCellHighlightBorder` — empty border (cell focus ring ပိတ်ရန်; row highlight ဖြင့်သာ selection ပြသည်)

Border override များကို theme တိုင်း ပြောင်းလဲစဉ် ပြန် apply သည် — သို့မှသာ အရောင်များသည် palette နောက်လိုက်သည်။

---

## 4. Theme ပြောင်းလဲပုံ (Theme Switch Sequence)

User က Header ရှိ sun/moon ခလုတ် နှိပ်လျှင် -

```
HeaderPanel (theme button)
    └── MainFrame.onThemeToggle()
          └── ThemeManager.toggle()
                └── setTheme(isDark ? LIGHT : DARK)
                      ├── header.refreshThemeIcon(ThemeManager.isDark())   ← icon လှန်ပြောင်း
                      └── (အောက်ပါ အဆင့် ၄ ဆင့်)
```

`setTheme()` အတွင်း **အဆင့်အလိုကောင်းစွာ** လုပ်ဆောင်သည် (animation ချောမွေ့ရန် order အရေးကြီးသည်) -

| အဆင့် | လုပ်ဆောင်ချက် | ရည်ရွယ်ချက် |
|---|---|---|
| ၁ | Showing window တိုင်း၏ old look ကို `BufferedImage` အဖြစ် snapshot ရိုက် (`window.printAll()`) | Crossfade အတွက် မှန်ဘုံငယ် |
| ၂ | Window အားလုံး (hidden/displayable ပါ) တွင် `SwingUtilities.updateComponentTreeUI()` + `repaint()` | Standard Swing widget များ ချက်ချင်း restyle — hidden window များပါ update လုပ်သဖြင့် ပြန်ဖွင့်လျှင် stale theme မရှိ |
| ၃ | `LISTENERS` အားလုံးကို notify (`Consumer<Theme>`) | Custom-paint component များ အရောင်ပြန်ဆွဲရန် |
| ၄ | နောက်ဆုံးမှ `crossfade()` စတင် | Heavy rebuild များ animation ကြားမှာ stutter မဖြစ်စေရန် |

> **Error handling:** Look & Feel set လုပ်ရန် မအောင်မြင်ပါက `ConfigurationException` ပစ်သည်။

---

## 5. Crossfade Animation

Window တစ်ခုစီ၏ old look ကို new look ပေါ်တွင် **fade-out** လုပ်သည် -

- **Glass pane** technique — window ပေါ်တွင် transparent `JComponent` တစ်ခု တင်၍ snapshot image ကို alpha 1 → 0 ဖြင့်ဆွဲသည်
- ကြာချိန် **360ms**၊ `javax.swing.Timer` ဖြင့် **16ms** တစ်ကြိမ် (~60 fps)
- Easing function — **easeInOutCubic** (စတင်/အဆုံး နှေး၊ အလယ် မြန်)
- `contains()` ကို `false` ပြန်သဖြင့် glass pane သည် **click-through** — animation ကာလအတွင်း UI ကို ပုံမှန်အတိုင်း အသုံးပြုနိုင်သည်
- Window တစ်ခုစီ **လွတ်လပ်စွာ** fade သည် — dialog ဖွင့်ထားလျှင် main frame ၏ animation မပျက်
- Fade လက်ရှိ run နေစဉ် ထပ် toggle ပါက — activeCrossfades map (`ConcurrentHashMap`, key = `RootPaneContainer`) မှ timer ရပ်ပြီး fade အသစ်က **အစားထိုး** သည်

```
crossfade(container, previousImage)
  ├── running fade ရှိ → timer.stop() + glass hide
  ├── glass pane = snapshot ကို alpha ဖြင့်ဆွဲသော JComponent
  ├── Timer (16ms) → progress = elapsed / 360
  │              → alpha = 1 − easeInOutCubic(progress)
  └── progress ≥ 1 → timer stop, glass hide, map မှ ဖယ်ရှား
```

---

## 6. Listener Pattern (Custom-paint Components အတွက်)

Standard Swing widget များကို `updateComponentTreeUI()` က အလိုအလျောက် restyle လုပ်ပေးသည်။ သို့သော် **ကိုယ်ပိုင် paint လုပ်သော** component များ (Sidebar, Dashboard card, Report table renderer စသည်) ကိုယ်ထူကိုယ်ထ အရောင်ပြန်ဆွဲရန် listener ထည့်ရသည် -

```java
// SidebarMenuPanel.java / ReportsPanel.java / DocumentsPanel.java တို့တွင် လိုက်နာသော pattern
private final Consumer<ThemeManager.Theme> themeListener =
        theme -> UiThread.runLater(() -> applyThemeColors());

@Override
public void addNotify() {
    super.addNotify();
    ThemeManager.addListener(themeListener);      // EDT-safe UiThread.runLater ဖြင့် wrap
}

@Override
public void removeNotify() {
    ThemeManager.removeListener(themeListener);   // memory leak မရှိစေရန် ဖယ်ရှား
    super.removeNotify();
}
```

**အရေးကြီး စည်းမျဉ်းများ**
- `addNotify()` တွင် register၊ `removeNotify()` တွင် unregister — မဟုတ်ပါက memory leak
- Listener body ကို `UiThread.runLater(...)` ဖြင့် EDT ပေါ် လည်ပတ်စေရန်
- `CopyOnWriteArrayList` သုံးထားသဖြင့် iteration ကာလအတွင်း listener ထည့်/ဖယ်လျှင်လည်း safe

---

## 7. Palette — Role အခြေခံ အရောင်စနစ်

Component များတွင် **hard-coded အရောင် မရေးရ**။ `Palette.color(Role)` မှတဆင့်သာ ရယူရမည် -

```java
label.setForeground(Palette.color(Role.TEXT_MUTED));
card.setBackground(Palette.color(Role.CARD_BG));
g.setColor(Palette.color(Role.CARD_BORDER));
```

`color(Role)` သည် လက်ရှိ theme (`ThemeManager.current()`) အတိုင်း အရောင်ပြန်ပေးသည်။

### 7.1 အဓိက Role များ

| Role | Light | Dark | အသုံးဝင်ရာ |
|---|---|---|---|
| `ACCENT` | `#2563EB` | `#3B82F6` | Primary buttons, links, active states |
| `ACCENT_SOFT` | `#EAF1FE` | rgba(59,130,246,**48**) | Selected rows, chips (dark တွင် translucent) |
| `SUCCESS` | `#16A34A` | `#10B981` | Active/approved status |
| `WARNING` | `#D97706` | `#F59E0B` | Pending/warning |
| `DANGER` | `#DC2626` | `#EF4444` | Error/rejected/delete |
| `INFO` | `#0891B2` | `#8B5CF6` | Informational accents |
| `CARD_BG` | `#FFFFFF` | `#1E1F22` | Card/panel နောက်ခံ |
| `CARD_BORDER` | `#E2E8F0` | `#2A2D32` | Border/grid line |
| `TEXT` | `#0F172A` | `#F8FAFC` | အဓိက စာသား |
| `TEXT_MUTED` | `#64748B` | `#94A3B8` | Secondary စာသား |
| `SURFACE_ALT` | `#F8FAFC` | `#121212` | Window/alternating surface |
| `SIDEBAR_BG` | `#F1F5F9` | `#18181A` | Sidebar နောက်ခံ |
| `SIDEBAR_ACTIVE_*` | white pill / slate | `#2A2D32` / `#F8FAFC` | Selected menu item |

### 7.2 Statistic Card Tints

Dashboard stat card များအတွက် accent အလိုက် အထူးပြု background/border များ -

- `Palette.statCardBackground(Role)` — dark တွင် muted tints (`#212431` blue, `#1C2925` emerald, `#2C2820` amber, `#2C1F22` red, `#25202E` purple); light တွင် pastel tints (`#F7FBFF`, `#F8FEFA`, ...)
- `Palette.statCardBorder(Role)` — accent ကို card surface နှင့် half-blend လုပ်ထားသော "quiet glow" border
- Role မသိပါက plain `CARD_BG` / `CARD_BORDER` သို့ fallback

### 7.3 Utility Methods

| Method | လုပ်ဆောင်ချက် |
|---|---|
| `readableForeground(Color bg)` | Background ပေါ်မှာ ဖတ်ရလွယ်သော အရောင် — luminance ≥ 140 ဆို BLACK၊ မဟုတ်လျှင် WHITE (accent fill ပေါ်ရှိ စာသား/icon များအတွက်) |
| `isDarkUi()` | `UIManager` ၏ `Panel.background` RGB ပေါင်း < 300 ဖြစ်လျှင် dark ဟု သတ်မှတ် (derived painter များအတွက်) |
| `accentSoft()` | `ACCENT_SOFT` shortcut |
| `errorOutline()` | FlatLaf error outline marker `"error"` (form validation အတွက်) |

---

## 8. FlatLaf Properties Layer

Standard widget များ၏ အသွင်အပြင်ကို properties ဖိုင်များဖြင့် ထိန်းညှိသည် -

**FlatLightLaf.properties / FlatDarkLaf.properties တွင် ပါဝင်သည်များ**

| Setting | တန်ဖိုး | ရှင်းလင်းချက် |
|---|---|---|
| `@accentColor` | light `#2563eb` / dark `#ffffff` | Focus ring, default button, checkbox စသည် |
| `Panel.background` | dark `#161616` | Window နောက်ခံ |
| `Table.background` | dark `#212121` | Card-tone table surface |
| `Table.alternateRowColor` | `#f8fafc` / `#161616` | Zebra stripe |
| `Table.selectionBackground` | dark `#7e7e7e` (+ black text) | Selection |
| `Component.arc / Button.arc` | `10` | Rounded corners |
| `Table.rowHeight` | `34` | Row အမြင့် |
| `ScrollBar.thumbArc` | `999` | ပုံသဏ္ဍာန် pill scrollbar (width 10) |
| `Popup.borderCornerRadius` | `10` | Dropdown/popup rounded |
| `Tooltip.cornerStyle` | `round` | Rounded tooltip |
| `ScrollPane.smoothScrolling` | `true` | Mouse-wheel smooth scroll |

Properties ဖိုင်ကို ပြင်လျှင် code ပြန် compile ရန် မလို (resource ဖြစ်သောကြောင့် rebuild/run သက်သာ)။

---

## 9. Icon စနစ် (SVG Icons)

Icon အားလုံးသည် `resources/icons/*.svg` တွင်ရှိပြီး **`currentColor` stroke** ဖြင့် ရေးထားသည် -

- FlatLaf ၏ `FlatSVGIcon` သည် `currentColor` ကို icon ကိုပြသထားသော component ၏ foreground color ဖြင့် အလိုအလျောက် ဆင်သည် — theme ပြောင်းလျှင် icon များ **ဘာမှလုပ်စရာမလို** ပြောင်းသည်
- `IconLoader.icon(name, size)` — `(name@size)` အလိုက် cache (`ConcurrentHashMap`)၊ immutable ဖြစ်သောကြောင့် share လုပ်ကား safe
- `IconLoader.tinted(name, size, color)` — theme မလိုက်နာရန် သတ်မှတ်အရောင် တိုက်ရိုက်ဆင်လိုလျှင် (separate cache — theme-following instance များကို mutate မလုပ်နိုင်)

**Sun/Moon toggle** — `HeaderPanel.refreshThemeIcon(dark)`:

```java
themeToggle.setIcon(IconLoader.tinted(dark ? "sun" : "moon", SIZE_DEFAULT,
        Palette.color(Role.SIDEBAR_FG)));
themeToggle.setToolTipText(dark ? "Switch to light theme" : "Switch to dark theme");
```

Dark mode တွင် sun icon (light သို့ပြန်သွားရန် ညွှန်ပြ)၊ light mode တွင် moon icon ပြသည်။

---

## 10. Chart Theming (JFreeChart)

JFreeChart object များသည် Swing UI delegate မရှိသဖြင့် `ChartTheme` မှ **build-time** တွင် Palette မှ အရောင်များ resolve သည် -

- `seriesColor(index)` — `ACCENT → SUCCESS → WARNING → INFO → DANGER` စဉ်လိုက် cycling
- `barColor()` — dark: `#60A5FA`; light: `blend(#94A3B8, #60A5FA, 0.45)`
- `pieSliceColor(label, index)` — status နာမည် (active/inactive) အတိုင်း theme တစ်ခုစီအတွက် **stable color**; မသိပါက per-theme fallback array
- Background/grid/label — `CARD_BG`, `CARD_BORDER`, `TEXT_MUTED`၊ font ကို `UIManager.defaultFont` မှ

**Dashboard သည် theme switch listener တွင် chart များကို အသစ်ပြန်ဆောက် (rebuild)** သဖြင့် chart အရောင်များ နောက်မကျသည်။

---

## 11. Developer Guide — Theme-aware Component အသစ်ထည့်ရန်

1. **အရောင်ရယူရန်** — `Palette.color(Role...)` သာ အသုံးပြုပါ။ Panel အတွင်း `new Color(0x......)` hard-code ခြင်း တင်းတင်း တားမြစ်သည်။
2. **Listener lifecycle** — Section 6 ရှိ pattern အတိုင်း `addNotify`/`removeNotify` တွင် register/unregister လုပ်ပါ။
3. **EDT safety** — listener body ကို `UiThread.runLater(...)` ဖြင့် wrap လုပ်ပါ။
4. **Chart/heavy painting** — repaint သာမက **rebuild** လုပ်ပါ (JFreeChart သည် color ပြန်လည်သတ်မှတ်မပေး)။
5. **Font** — `UIManager.getFont("defaultFont")` မှ derive လုပ်ပါ။
6. **စစ်ဆေးရန်** — Light/Dark နှစ်မျိုးလုံးတွင် စမ်းပါ။ Smoke tools (`com.ams.hrms.tools.*SmokeTool`) များသည် `ThemeManager.install()` / `toggle()` / `setTheme(DARK)` ခေါ်၍ စမ်းသပ်ရန် အသုံးဝင်သည် (ဥပမာ `ThemeFadeSmokeTool` သည် theme ကူးပြောင်းမှု animation ကို အလိုအလျောက် flip ပြသည်)။

---

## 12. Thread Safety & Implementation Notes

| အချက် | အကောင်အထည်ဖော်မှု |
|---|---|
| `current` theme | `volatile` — thread များကြား မြင်ရမှု သေချာစေရန် |
| Listeners | `CopyOnWriteArrayList` — iteration ကာလအတွင်း ပြင်ဆင်မှု safe |
| Active crossfades | `ConcurrentHashMap<RootPaneContainer, ActiveCrossfade>` — window တစ်ခုစီ fade လွတ်လပ် |
| `install()` | `synchronized` + `installed` flag — နှစ်ခါခေါ်ပါက ဒုတိယအကြိမ် ချန်လှန် |
| Snapshot | `TYPE_INT_RGB BufferedImage` + `printAll(Graphics)` — offscreen render |

---

## 13. အနှစ်ချုပ် Diagram

```
                        ┌─────────────────────────────┐
                        │   Bootstrapper.install()    │
                        └──────────────┬──────────────┘
                                       ▼
                        ┌─────────────────────────────┐
     Sun/Moon button ──►│       ThemeManager          │
     (HeaderPanel)      │  install / toggle / setTheme│
                        └──┬──────────┬───────────┬───┘
                           ▼          ▼           ▼
                 FlatLaf properties  Palette   LISTENERS
                 (stock widgets)   (custom UI)  (Sidebar/Reports/
                           ▲          ▲         Documents/Dashboard…)
                           └────┬─────┘
                                ▼
                    Crossfade animation (360ms, easeInOutCubic)
                    — window တစ်ခုချင်း လွတ်လပ်စွာ fade
```
