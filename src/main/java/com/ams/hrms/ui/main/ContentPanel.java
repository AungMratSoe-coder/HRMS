package com.ams.hrms.ui.main;

import java.awt.CardLayout;
import java.awt.BorderLayout;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import javax.swing.JPanel;

/**
 * CardLayout content area with lazy panel creation (spec section 50). Panels
 * are built on first visit via their factory and cached; a module can also be
 * rebuilt on demand (refresh) or shown transiently (access-denied notices)
 * without entering the cache.
 */
public class ContentPanel extends JPanel {

    private final Map<String, Supplier<JPanel>> factories = new HashMap<>();
    private final Map<String, JPanel> cachedPanels = new HashMap<>();
    private String currentId;

    public ContentPanel() {
        super(new CardLayout());
    }

    private CardLayout cards() {
        return (CardLayout) getLayout();
    }

    /** Registers the factory that lazily builds a module's panel. */
    public void register(String id, Supplier<JPanel> factory) {
        factories.put(id, factory);
    }

    public boolean isRegistered(String id) {
        return factories.containsKey(id);
    }

    /** Shows the module for {@code id}, creating its panel on first visit. */
    public void show(String id) {
        JPanel panel = cachedPanels.get(id);
        if (panel == null) {
            Supplier<JPanel> factory = factories.get(id);
            if (factory == null) {
                throw new IllegalStateException("No panel factory registered for '" + id + "'");
            }
            panel = factory.get();
            cachedPanels.put(id, panel);
            add(panel, id);
        }
        cards().show(this, id);
        currentId = id;
    }

    /**
     * Rebuilds and shows the module's panel from its factory (data reload,
     * theme-sensitive content, ...). No-op when the id has no factory.
     */
    public void refresh(String id) {
        Supplier<JPanel> factory = factories.get(id);
        if (factory == null) {
            return;
        }
        JPanel existing = cachedPanels.remove(id);
        if (existing != null) {
            remove(existing);
        }
        JPanel rebuilt = factory.get();
        cachedPanels.put(id, rebuilt);
        add(rebuilt, id);
        cards().show(this, id);
        revalidate();
        repaint();
    }

    /**
     * Shows a one-off panel that is not part of the module cache (e.g.
     * permission-denied notice); showing another transient replaces the
     * previous one.
     */
    public void showTransient(String key, JPanel panel) {
        synchronized (getTreeLock()) {
            for (var component : getComponents()) {
                if (key.equals(component.getName())) {
                    remove(component);
                    break;
                }
            }
        }
        panel.setName(key);
        add(panel, key);
        cards().show(this, key);
        revalidate();
        repaint();
    }

    /** The currently visible module id (also set for transients). */
    public String currentId() {
        return currentId;
    }
}
