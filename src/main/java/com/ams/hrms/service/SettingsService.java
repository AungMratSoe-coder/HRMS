package com.ams.hrms.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ams.hrms.event.EventBus;
import com.ams.hrms.event.Events;
import com.ams.hrms.exception.BusinessException;
import com.ams.hrms.exception.ValidationException;
import com.ams.hrms.model.AppSetting;
import com.ams.hrms.repository.SettingsRepository;
import com.ams.hrms.repository.TransactionManager;
import com.ams.hrms.security.Permissions;
import com.ams.hrms.security.SecurityService;
import com.ams.hrms.security.SessionContext;

/**
 * Application settings (spec section 8, SYSTEM category): reads the seeded
 * {@code app_settings} rows and applies validated value changes as one
 * all-or-nothing transaction. Every individual change is audited with its
 * old and new value.
 */
public class SettingsService {

    public static final String DATA_SCOPE = "settings";

    private static final Logger LOG = LoggerFactory.getLogger(SettingsService.class);

    private final SettingsRepository settingsRepository;
    private final AuditService auditService;

    public SettingsService(SettingsRepository settingsRepository, AuditService auditService) {
        this.settingsRepository = settingsRepository;
        this.auditService = auditService;
    }

    public List<AppSetting> findAll() {
        SecurityService.require(Permissions.SETTINGS_MANAGE);
        return settingsRepository.findAll();
    }

    /**
     * Applies the given key → raw value pairs. Unchanged keys are ignored;
     * every changed key must validate before anything is written. Returns
     * the number of settings actually updated.
     */
    public int saveAll(Map<String, String> valuesByKey) {
        SecurityService.require(Permissions.SETTINGS_MANAGE);

        List<AppSetting> current = settingsRepository.findAll();
        Map<String, AppSetting> byKey = new LinkedHashMap<>();
        for (AppSetting setting : current) {
            byKey.put(setting.getKey(), setting);
        }

        List<SettingChange> changes = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        for (Map.Entry<String, String> entry : valuesByKey.entrySet()) {
            AppSetting setting = byKey.get(entry.getKey());
            if (setting == null) {
                errors.add("Unknown setting '" + entry.getKey() + "'.");
                continue;
            }
            String normalized = SettingsValidator.normalize(setting.getValueType(),
                    entry.getValue());
            if (normalized.equals(setting.getValue())) {
                continue;
            }
            String label = friendlyLabel(setting.getKey());
            errors.addAll(SettingsValidator.validate(setting.getKey(),
                    setting.getValueType(), entry.getValue(), label));
            changes.add(new SettingChange(setting, normalized));
        }
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
        if (changes.isEmpty()) {
            return 0;
        }

        Long userId = SessionContext.currentUserId();
        TransactionManager.execute(tx -> {
            for (SettingChange change : changes) {
                settingsRepository.updateValue(tx, change.setting().getKey(),
                        change.newValue(), userId);
            }
            return null;
        });

        for (SettingChange change : changes) {
            AppSetting setting = change.setting();
            auditService.record("UPDATE", DATA_SCOPE.toUpperCase(), "AppSetting",
                    setting.getId(), "'" + setting.getKey() + "' changed from '"
                            + display(setting.getValue()) + "' to '"
                            + display(change.newValue()) + "'");
        }
        LOG.info("{} setting(s) updated", changes.size());
        EventBus.publish(new Events.DataChanged(DATA_SCOPE));
        return changes.size();
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** "payroll.tax_rate_percent" → "Payroll Tax Rate Percent"; keeps the category word. */
    public static String friendlyLabel(String key) {
        String[] words = key.split("[._]");
        StringBuilder label = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if (!label.isEmpty()) {
                label.append(' ');
            }
            label.append(Character.toUpperCase(word.charAt(0)))
                    .append(word.substring(1));
        }
        return label.toString();
    }

    private static String display(String storedValue) {
        return storedValue == null || storedValue.isBlank() ? "(blank)" : storedValue;
    }

    private record SettingChange(AppSetting setting, String newValue) {
    }
}
