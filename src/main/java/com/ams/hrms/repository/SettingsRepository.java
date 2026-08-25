package com.ams.hrms.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import com.ams.hrms.model.AppSetting;

/**
 * Application setting persistence (table {@code app_settings}). Settings are
 * seeded by migration; the application only reads and updates values.
 */
public class SettingsRepository {

    private static final String SELECT =
            "SELECT s.id, s.setting_key, s.setting_value, s.value_type, s.category, "
                    + "s.description, s.updated_at, u.username AS updated_by_name "
                    + "FROM app_settings s LEFT JOIN users u ON u.id = s.updated_by";

    /** All settings ordered for stable tab/row layout. */
    public List<AppSetting> findAll() {
        return new Sql().list(SELECT + " ORDER BY FIELD(s.category, 'COMPANY', 'PAYROLL', "
                        + "'ATTENDANCE', 'LEAVE', 'DOCUMENTS', 'GENERAL'), s.setting_key",
                this::mapRow);
    }

    public Optional<AppSetting> findByKey(String key) {
        return new Sql().first(SELECT + " WHERE s.setting_key = ?", this::mapRow, key);
    }

    /**
     * Writes a new value inside the given transaction; {@code userId} may be
     * null only when no session exists (never in normal flows).
     */
    public void updateValue(Sql sql, String key, String value, Long userId) {
        sql.executeUpdate(
                "UPDATE app_settings SET setting_value = ?, updated_by = ? WHERE setting_key = ?",
                value, userId, key);
    }

    private AppSetting mapRow(ResultSet rs) throws SQLException {
        AppSetting setting = new AppSetting();
        setting.setId(rs.getLong("id"));
        setting.setKey(rs.getString("setting_key"));
        setting.setValue(rs.getString("setting_value"));
        setting.setValueType(rs.getString("value_type"));
        setting.setCategory(rs.getString("category"));
        setting.setDescription(rs.getString("description"));
        setting.setUpdatedByName(rs.getString("updated_by_name"));
        setting.setUpdatedAt(rs.getObject("updated_at", java.time.LocalDateTime.class));
        return setting;
    }
}
