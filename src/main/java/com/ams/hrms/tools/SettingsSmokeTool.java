package com.ams.hrms.tools;

import java.util.LinkedHashMap;
import java.util.Map;

import com.ams.hrms.config.AppConfig;
import com.ams.hrms.config.DatabaseConfig;
import com.ams.hrms.config.ServiceRegistry;
import com.ams.hrms.db.DatabaseMigrator;
import com.ams.hrms.db.DbChecker;
import com.ams.hrms.exception.AuthorizationException;
import com.ams.hrms.exception.ValidationException;
import com.ams.hrms.model.AppSetting;
import com.ams.hrms.repository.SettingsRepository;
import com.ams.hrms.service.AuthService;
import com.ams.hrms.service.SettingsService;

/**
 * Development-only Settings module verification against the live database:
 * load of seeded rows, validation rejections (type, range, currency,
 * timezone, boolean), a valid change round-trip (apply then revert),
 * unchanged-key no-op counting and RBAC denial for the officer account.
 */
public final class SettingsSmokeTool {

    private static int failures;

    public static void main(String[] args) {
        AppConfig config = AppConfig.get();
        DbChecker.check(config);
        DatabaseConfig.initialize(config);
        DatabaseMigrator.migrate(config);
        ServiceRegistry.initialize();

        AuthService auth = ServiceRegistry.authService();
        SettingsService settings = ServiceRegistry.settingsService();

        // --- load as admin ---------------------------------------------------
        auth.login("admin@ams.local", "Admin@123");
        var all = settings.findAll();
        check("admin loads " + all.size() + " seeded settings",
                () -> all.size() >= 15);
        check("categories are grouped COMPANY..GENERAL",
                () -> all.get(0).getCategory().equals("COMPANY")
                        && all.get(all.size() - 1).getKey().startsWith("app."));

        String originalTax = valueOf(all, "payroll.tax_rate_percent");
        String originalCurrency = valueOf(all, "payroll.currency");
        String originalCarryForward = valueOf(all, "leave.carry_forward_enabled");

        // --- validation rejections ------------------------------------------
        Map<String, String> bad = new LinkedHashMap<>();
        bad.put("payroll.tax_rate_percent", "abc");
        checkThrows("reject non-numeric tax rate",
                () -> settings.saveAll(bad), ValidationException.class);

        bad.clear();
        bad.put("payroll.tax_rate_percent", "150");
        checkThrows("reject tax rate above 100%",
                () -> settings.saveAll(bad), ValidationException.class);

        bad.clear();
        bad.put("payroll.currency", "usd");
        checkThrows("reject lowercase currency code",
                () -> settings.saveAll(bad), ValidationException.class);

        bad.clear();
        bad.put("app.timezone", "Mars/Olympus");
        checkThrows("reject invalid timezone",
                () -> settings.saveAll(bad), ValidationException.class);

        bad.clear();
        bad.put("leave.carry_forward_enabled", "maybe");
        checkThrows("reject non-boolean carry-forward",
                () -> settings.saveAll(bad), ValidationException.class);

        bad.clear();
        bad.put("company.name", "");
        checkThrows("reject blank company name",
                () -> settings.saveAll(bad), ValidationException.class);

        bad.clear();
        bad.put("no.such.key", "1");
        checkThrows("reject unknown setting key",
                () -> settings.saveAll(bad), ValidationException.class);

        // --- valid change round-trip ----------------------------------------
        Map<String, String> change = new LinkedHashMap<>();
        change.put("payroll.tax_rate_percent", originalTax.equals("7") ? "8" : "7");
        int updated = settings.saveAll(change);
        check("one setting updated", () -> updated == 1);
        check("change persisted",
                () -> "7".equals(new SettingsRepository()
                        .findByKey("payroll.tax_rate_percent").orElseThrow()
                        .getValue()));

        Map<String, String> mixed = new LinkedHashMap<>();
        mixed.put("payroll.tax_rate_percent", originalTax);
        mixed.put("payroll.currency", originalCurrency);
        check("unchanged keys are no-ops",
                () -> settings.saveAll(mixed) == 1);

        Map<String, String> revert = new LinkedHashMap<>();
        revert.put("payroll.tax_rate_percent", originalTax);
        revert.put("payroll.currency", originalCurrency);
        revert.put("leave.carry_forward_enabled", originalCarryForward);
        check("submitting originals again is a no-op",
                () -> settings.saveAll(revert) == 0);
        check("final values equal originals",
                () -> valueOf(settings.findAll(), "payroll.tax_rate_percent")
                        .equals(originalTax)
                        && valueOf(settings.findAll(), "payroll.currency")
                                .equals(originalCurrency)
                        && valueOf(settings.findAll(), "leave.carry_forward_enabled")
                                .equals(originalCarryForward));

        auth.logout();

        // --- RBAC: officer must be denied ------------------------------------
        RbacSmokeTool.provisionOfficerAccount();
        auth.login("officer@ams.local", "Officer@123");
        checkThrows("officer denied reading settings",
                () -> settings.findAll(), AuthorizationException.class);
        Map<String, String> sneaky = new LinkedHashMap<>();
        sneaky.put("payroll.tax_rate_percent", "0");
        checkThrows("officer denied saving settings",
                () -> settings.saveAll(sneaky), AuthorizationException.class);
        auth.logout();

        System.out.println(failures == 0
                ? "ALL SETTINGS SMOKE CHECKS PASSED"
                : failures + " SETTING(S) OF SMOKE CHECKS FAILED");
        DatabaseConfig.close();
        System.exit(failures == 0 ? 0 : 1);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static String valueOf(java.util.List<AppSetting> settings, String key) {
        return settings.stream()
                .filter(setting -> setting.getKey().equals(key))
                .findFirst().orElseThrow()
                .getValue();
    }

    private interface ThrowingRunnable {

        void run() throws Exception;
    }

    private static void check(String label, java.util.function.BooleanSupplier assertion) {
        try {
            if (assertion.getAsBoolean()) {
                System.out.println("OK   " + label);
            } else {
                failures++;
                System.out.println("FAIL " + label);
            }
        } catch (RuntimeException e) {
            failures++;
            System.out.println("FAIL " + label + " (" + e.getMessage() + ")");
        }
    }

    private static void checkThrows(String label, ThrowingRunnable action,
                                    Class<? extends Exception> expected) {
        try {
            action.run();
            failures++;
            System.out.println("FAIL " + label + " (no exception thrown)");
        } catch (Exception thrown) {
            if (expected.isInstance(thrown)) {
                System.out.println("OK   " + label);
            } else {
                failures++;
                System.out.println("FAIL " + label + " (unexpected "
                        + thrown.getClass().getSimpleName() + ": " + thrown.getMessage() + ")");
            }
        }
    }
}
