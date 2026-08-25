package com.ams.hrms.ui.main;

import java.util.function.Consumer;

import javax.swing.JPanel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ams.hrms.component.SidebarMenuPanel;
import com.ams.hrms.event.EventBus;
import com.ams.hrms.event.Events;
import com.ams.hrms.security.SessionContext;

/**
 * Central navigation API (spec section 50). The sidebar, header title, frame
 * title and content area all react through this service; nothing else holds
 * cross-references. Permission is re-checked on every navigation attempt so
 * a stale selection can never bypass RBAC.
 */
public class NavigationService {

    private static final Logger LOG = LoggerFactory.getLogger(NavigationService.class);

    private final ContentPanel contentPanel;
    private final Consumer<String> titleHandler;
    private Consumer<String> selectionSyncHandler;

    private String currentId;

    public NavigationService(ContentPanel contentPanel, Consumer<String> titleHandler) {
        this.contentPanel = contentPanel;
        this.titleHandler = titleHandler;
    }

    /**
     * Registers a callback that keeps the sidebar's active highlight in sync
     * when navigation is triggered programmatically (deep links, shortcuts).
     */
    public void onNavigated(Consumer<String> handler) {
        this.selectionSyncHandler = handler;
    }

    /**
     * Navigates to the module with the given menu id.
     *
     * @return true when the module was shown; false when access was denied
     */
    public boolean navigate(String id) {
        SidebarMenuPanel.MenuItem item = MenuDefinition.byId(id);
        if (item == null) {
            LOG.warn("Navigation requested for unknown menu id '{}'", id);
            return false;
        }
        if (item.requiredPermission() != null
                && !SessionContext.has(item.requiredPermission())) {
            showAccessDenied(item);
            return false;
        }
        // Management-only consoles are unreachable for self-service accounts,
        // even programmatically (deep links, stale shortcuts).
        if (MenuDefinition.hiddenForSelfService(id)
                && SessionContext.hasOnlyRole("EMPLOYEE")) {
            LOG.warn("Module '{}' is not available to self-service accounts", id);
            return false;
        }

        if (!contentPanel.isRegistered(id)) {
            contentPanel.register(id, () -> buildPlaceholder(item));
        }
        contentPanel.show(id);
        currentId = id;
        titleHandler.accept(item.label());
        if (selectionSyncHandler != null) {
            selectionSyncHandler.accept(id);
        }
        EventBus.publish(new Events.NavigationChanged(id));
        return true;
    }

    /** Rebuilds the current module's panel (F5). */
    public void refreshCurrent() {
        if (currentId != null) {
            contentPanel.refresh(currentId);
        }
    }

    public String currentId() {
        return currentId;
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    private void showAccessDenied(SidebarMenuPanel.MenuItem item) {
        JPanel denied = new JPanel(new net.miginfocom.swing.MigLayout("wrap 1, align center center"));
        denied.setOpaque(false);
        denied.add(new com.ams.hrms.component.EmptyStatePanel("warning", "Access denied",
                "Your role does not include " + item.requiredPermission().name()
                        + ", which is required for the " + item.label() + " module."));
        contentPanel.showTransient("denied:" + item.id(), denied);
        currentId = item.id();
        titleHandler.accept(item.label());
    }

    /** Default pending-module screen for ids without a real panel yet. */
    private JPanel buildPlaceholder(SidebarMenuPanel.MenuItem item) {
        JPanel placeholder = new JPanel(new net.miginfocom.swing.MigLayout("wrap 1, align center center"));
        placeholder.setOpaque(false);
        placeholder.add(new com.ams.hrms.component.EmptyStatePanel(item.iconKey(), item.label() + " module",
                "This module is delivered in its own implementation phase."));
        return placeholder;
    }
}
