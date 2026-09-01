-- Backup & restore tooling configuration (Settings > Backup & Restore).
-- Empty paths mean "resolve the tool from the system PATH".

INSERT INTO app_settings (setting_key, setting_value, value_type, category, description, updated_by) VALUES
    ('backup.mysqldump_path', '', 'STRING', 'GENERAL',
     'Full path to mysqldump.exe used by Backup Now. Leave empty to use the PATH.',
     (SELECT id FROM users WHERE username = 'admin')),
    ('backup.mysql_path', '', 'STRING', 'GENERAL',
     'Full path to mysql.exe used by Restore. Leave empty to use the PATH.',
     (SELECT id FROM users WHERE username = 'admin'));
