package com.ams.hrms.component;

import com.ams.hrms.security.Permissions;
import com.ams.hrms.security.SecurityService;

/**
 * Action button that disables itself when the current session lacks the
 * required permission (UI gate). Service methods must still call
 * {@link SecurityService#require} - this is convenience, not enforcement.
 */
public class SecureButton extends ModernButton {

    private final Permissions requiredPermission;

    public SecureButton(String text, String iconName, Variant variant, Permissions requiredPermission) {
        super(text, iconName, variant);
        this.requiredPermission = requiredPermission;
        applySecurity();
    }

    public SecureButton(String text, Variant variant, Permissions requiredPermission) {
        super(text, variant);
        this.requiredPermission = requiredPermission;
        applySecurity();
    }

    /** Re-evaluates against the current session (e.g. after re-login in tests). */
    public final void refreshSecurity() {
        applySecurity();
    }

    private void applySecurity() {
        boolean allowed = SecurityService.can(requiredPermission);
        setEnabled(allowed);
        setToolTipText(allowed
                ? getToolTipText()
                : "Requires permission: " + requiredPermission.name());
    }
}
