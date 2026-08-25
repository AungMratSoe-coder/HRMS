package com.ams.hrms.model;

import java.time.LocalDateTime;

/**
 * One editable application setting row from {@code app_settings}. Values are
 * stored as strings and interpreted according to {@code valueType}
 * (STRING, NUMBER or BOOLEAN).
 */
public class AppSetting {

    public static final String TYPE_STRING = "STRING";
    public static final String TYPE_NUMBER = "NUMBER";
    public static final String TYPE_BOOLEAN = "BOOLEAN";

    private Long id;
    private String key;
    private String value;
    private String valueType;
    private String category;
    private String description;
    private String updatedByName;
    private LocalDateTime updatedAt;

    public boolean isBoolean() {
        return TYPE_BOOLEAN.equals(valueType);
    }

    public boolean isNumber() {
        return TYPE_NUMBER.equals(valueType);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getValueType() {
        return valueType;
    }

    public void setValueType(String valueType) {
        this.valueType = valueType;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getUpdatedByName() {
        return updatedByName;
    }

    public void setUpdatedByName(String updatedByName) {
        this.updatedByName = updatedByName;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
