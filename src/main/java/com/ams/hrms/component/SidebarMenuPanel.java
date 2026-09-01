package com.ams.hrms.component;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;

import com.ams.hrms.ui.theme.Palette;
import com.ams.hrms.ui.theme.Palette.Role;
import com.ams.hrms.ui.theme.ThemeManager;
import com.ams.hrms.util.IconLoader;
import com.ams.hrms.util.UiGraphics;
import com.ams.hrms.util.UiThread;

import net.miginfocom.swing.MigLayout;

/**
 * Collapsible sidebar navigation (spec section 6). Owns its own animation,
 * selection state and rendering; the main frame only listens for selection
 * changes. Buttons are created once per menu definition - never recreated
 * during expand/collapse - so there is no layout thrash and no memory leak.
 */
public class SidebarMenuPanel extends JPanel {

    /**
     * One navigation entry.
     *
     * @param requiredPermission permission needed to see/use this entry;
     *                           null = any authenticated user
     */
    public record MenuItem(String id, String iconKey, String label,
            com.ams.hrms.security.Permissions requiredPermission) {
    }

    /** Visual grouping of menu entries shown in the expanded sidebar. */
    private record MenuSection(String title, List<MenuItem> items) {
    }

    /**
     * Presentation-only section layout: each row is the section title
     * followed by the menu ids it contains. Navigation ids and permissions
     * stay owned by {@code MenuDefinition}; this map only decides where
     * buttons sit. Sections whose ids were filtered out upstream
     * (permissions) simply do not render; ids missing here still appear in
     * an unlabeled trailing group so new modules can never silently
     * disappear from the nav.
     */
    private static final String[][] SECTION_LAYOUT = {
            { "Main", "dashboard" },
            { "People", "employees", "departments", "positions" },
            { "Hiring", "recruitment", "onboarding" },
            { "Time & Attendance", "attendance", "shifts", "overtime", "leave" },
            { "Payroll", "payroll", "payslips" },
            { "Performance & Development", "performance", "training" },
            { "Administration", "assets", "documents", "separation" },
            { "Reports & Security", "reports", "audit" },
            { "System", "settings" } };

    public static final int EXPANDED_WIDTH = 240;
    /** Wide enough for a 20px icon plus the always-visible slim scrollbar. */
    public static final int COLLAPSED_WIDTH = 80;
    private static final int ITEM_HEIGHT = 44;
    private static final int ANIMATION_MS = 220;
    private static final int TICK_MS = 16;

    private final Map<String, MenuButton> buttonsById = new LinkedHashMap<>();
    private final List<Runnable> collapseListeners = new ArrayList<>();
    private final List<JLabel> sectionHeaders = new ArrayList<>();
    private JScrollPane menuScroll;

    private final JPanel footerStack = new JPanel(new MigLayout("wrap 1, insets 0, gap 3", "[grow,fill]"));
    private final MenuButton logoutButton = new MenuButton(null, "logout", "Logout");
    private final JLabel brandLabel = new JLabel("HRMS");

    private Consumer<String> selectionListener;
    private Runnable logoutHandler;

    private final Consumer<ThemeManager.Theme> themeListener = theme -> UiThread.runLater(() -> {
        brandLabel.setForeground(Palette.color(Role.SIDEBAR_ACTIVE_FG));
        for (JLabel header : sectionHeaders) {
            header.setForeground(Palette.color(Role.SIDEBAR_FG_MUTED));
        }
    });

    private boolean expanded = true;
    /**
     * Master switch for all text (menu labels, section captions). Turned off
     * the moment a collapse starts and on again only once the sidebar is
     * fully expanded, so labels never render partially clipped mid-animation.
     */
    private boolean labelsVisible = true;
    private String selectedId;

    private Timer animator;
    private int animationFromWidth;
    private int animationTargetWidth;
    private long animationStartMillis;

    public SidebarMenuPanel(List<MenuItem> items) {
        // BorderLayout pins the footer (logout) to the bottom of
        // the card while the menu list scrolls in the center, so the logout
        // button stays reachable even when the module list overflows.
        super(new BorderLayout(0, 10));
        setBorder(new EmptyBorder(18, 12, 14, 12));

        brandLabel.setForeground(Palette.color(Role.SIDEBAR_ACTIVE_FG));
        brandLabel.setFont(brandLabel.getFont().deriveFont(Font.BOLD, 15f));

        JPanel brandBar = new JPanel(new BorderLayout());
        brandBar.setOpaque(false);
        brandBar.setBorder(new EmptyBorder(0, 10, 0, 0));
        brandBar.setPreferredSize(new Dimension(EXPANDED_WIDTH, 36));
        brandBar.add(brandLabel, BorderLayout.CENTER);

        JPanel itemStack = new ScrollableStack();
        for (MenuSection section : groupItems(items)) {
            if (!section.items().isEmpty() && !section.title().isEmpty()) {
                itemStack.add(sectionHeader(section.title()), "gaptop 10, gapbottom 1");
            }
            for (MenuItem item : section.items()) {
                MenuButton button = new MenuButton(item.id(), item.iconKey(), item.label());
                button.setAction(() -> select(item.id()));
                buttonsById.put(item.id(), button);
                itemStack.add(button);
            }
        }

        menuScroll = new JScrollPane(itemStack);
        menuScroll.setBorder(null);
        menuScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        menuScroll.getVerticalScrollBar().setUnitIncrement(16);
        // Slim scrollbar so the collapsed icon rail keeps clear space around it.
        menuScroll.getVerticalScrollBar().setPreferredSize(new Dimension(8, 0));
        menuScroll.setOpaque(false);
        menuScroll.getViewport().setOpaque(false);

        logoutButton.setAction(() -> {
            if (logoutHandler != null) {
                logoutHandler.run();
            }
        });
        footerStack.setOpaque(false);
        footerStack.add(logoutButton);

        setOpaque(false);
        add(brandBar, BorderLayout.NORTH);
        add(menuScroll, BorderLayout.CENTER);
        add(footerStack, BorderLayout.SOUTH);

        setPreferredSize(new Dimension(EXPANDED_WIDTH, getPreferredSize().height));
        updateTextVisibility();
    }

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    @Override
    public void addNotify() {
        super.addNotify();
        ThemeManager.addListener(themeListener);
    }

    @Override
    public void removeNotify() {
        ThemeManager.removeListener(themeListener);
        super.removeNotify();
    }

    /** Registers the handler invoked when a menu entry is chosen. */
    public void onSelection(Consumer<String> listener) {
        this.selectionListener = listener;
    }

    /** Registers the handler for the footer logout action. */
    public void onLogout(Runnable handler) {
        this.logoutHandler = handler;
    }

    /** Selects a menu entry programmatically and fires the listener. */
    public void select(String id) {
        applySelection(id);
        if (selectionListener != null) {
            selectionListener.accept(id);
        }
    }

    /**
     * Updates the active highlight without firing the selection listener (sync from
     * NavigationService).
     */
    public void setSelectedId(String id) {
        if (!buttonsById.containsKey(id)) {
            return;
        }
        applySelection(id);
    }

    private void applySelection(String id) {
        MenuButton target = buttonsById.get(id);
        if (target == null) {
            return;
        }
        selectedId = id;
        for (Map.Entry<String, MenuButton> entry : buttonsById.entrySet()) {
            entry.getValue().setSelected(entry.getKey().equals(id));
        }
        repaint();
    }

    public String selectedId() {
        return selectedId;
    }

    public boolean isExpanded() {
        return expanded;
    }

    /** Expands or collapses the sidebar with a smooth animation. */
    public void setExpanded(boolean shouldExpand) {
        if (this.expanded == shouldExpand || (animator != null && animator.isRunning())) {
            return;
        }
        this.expanded = shouldExpand;
        animationFromWidth = getWidth() > 0 ? getWidth() : currentPreferredWidth();
        animationTargetWidth = shouldExpand ? EXPANDED_WIDTH : COLLAPSED_WIDTH;
        animationStartMillis = System.currentTimeMillis();

        if (!shouldExpand) {
            labelsVisible = false;
            updateTextVisibility();
        }
        collapseListeners.forEach(Runnable::run);

        animator = new Timer(TICK_MS, event -> stepAnimation());
        animator.start();
    }

    public void toggleExpanded() {
        setExpanded(!expanded);
    }

    /** Registers a listener notified when expansion state changes. */
    public void onCollapseStateChange(Runnable listener) {
        collapseListeners.add(listener);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        UiGraphics.fillRoundRect(g, 0, 0, getWidth(), getHeight(), 14, Palette.color(Role.SIDEBAR_BG));
        g.dispose();
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    /**
     * Groups the caller's (already permission-filtered) items into sections
     * following {@link #SECTION_LAYOUT}; ids not listed anywhere keep working
     * by joining a final unlabeled group (no header rendered).
     */
    private static List<MenuSection> groupItems(List<MenuItem> items) {
        Map<String, MenuItem> byId = new LinkedHashMap<>();
        for (MenuItem item : items) {
            byId.put(item.id(), item);
        }
        List<MenuSection> sections = new ArrayList<>();
        Set<String> grouped = new HashSet<>();
        for (String[] section : SECTION_LAYOUT) {
            List<MenuItem> members = new ArrayList<>();
            for (int i = 1; i < section.length; i++) {
                MenuItem item = byId.get(section[i]);
                if (item != null) {
                    members.add(item);
                    grouped.add(section[i]);
                }
            }
            sections.add(new MenuSection(section[0], members));
        }
        List<MenuItem> ungrouped = new ArrayList<>();
        for (MenuItem item : items) {
            if (!grouped.contains(item.id())) {
                ungrouped.add(item);
            }
        }
        if (!ungrouped.isEmpty()) {
            sections.add(new MenuSection("", ungrouped));
        }
        return sections;
    }

    /** Small muted caption above a section; never focusable or selectable. */
    private JLabel sectionHeader(String title) {
        JLabel header = new JLabel(title.toUpperCase());
        header.setForeground(Palette.color(Role.SIDEBAR_FG_MUTED));
        header.setFont(header.getFont().deriveFont(Font.BOLD, 10f));
        header.setBorder(new EmptyBorder(0, 10, 0, 0));
        sectionHeaders.add(header);
        return header;
    }

    private void stepAnimation() {
        double progress = Math.min(1.0,
                (System.currentTimeMillis() - animationStartMillis) / (double) ANIMATION_MS);
        double eased = easeOutCubic(progress);
        int width = (int) Math.round(animationFromWidth
                + (animationTargetWidth - animationFromWidth) * eased);
        setPreferredSize(new Dimension(width, getPreferredSize().height));
        revalidate();
        if (getParent() != null) {
            getParent().revalidate();
            getParent().repaint();
        }
        if (progress >= 1.0) {
            animator.stop();
            animator = null;
            if (expanded) {
                labelsVisible = true;
                updateTextVisibility();
            }
        }
    }

    private void updateTextVisibility() {
        for (MenuButton button : buttonsById.values()) {
            button.setToolTipText(expanded ? null : button.label());
        }
        logoutButton.setToolTipText(expanded ? null : "Logout");
        // Section captions only make sense with visible labels; collapsed
        // mode drops them entirely so only the icon rail remains.
        for (JLabel header : sectionHeaders) {
            header.setVisible(labelsVisible);
        }
        revalidate();
        repaint();
    }

    private int currentPreferredWidth() {
        return expanded ? EXPANDED_WIDTH : COLLAPSED_WIDTH;
    }

    private static double easeOutCubic(double progress) {
        return 1 - Math.pow(1 - progress, 3);
    }

    /**
     * Vertical menu stack inside the sidebar scroll pane. Always tracks the
     * viewport width so buttons resize with expand/collapse (labels are
     * flag-controlled, see {@link #labelsVisible}) and only the height
     * scrolls.
     */
    private static final class ScrollableStack extends JPanel implements Scrollable {

        private ScrollableStack() {
            super(new MigLayout("wrap 1, insets 0, gap 3", "[grow,fill]"));
            setOpaque(false);
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) {
            return 16;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
            return orientation == SwingConstants.VERTICAL ? visible.height : visible.width;
        }
    }

    /**
     * One sidebar row: custom-painted hover/active states, icon inheriting the
     * foreground color, label shown when there is room for it.
     */
    private final class MenuButton extends JComponent {

        private final String label;
        private String iconKey;
        private Runnable action;
        private boolean hovered;
        private boolean selected;

        MenuButton(String id, String iconKey, String label) {
            this.label = label;
            this.iconKey = iconKey;
            setOpaque(false);
            setPreferredSize(new Dimension(EXPANDED_WIDTH - 24, ITEM_HEIGHT));
            setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));

            addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mouseEntered(java.awt.event.MouseEvent event) {
                    hovered = true;
                    repaint();
                }

                @Override
                public void mouseExited(java.awt.event.MouseEvent event) {
                    hovered = false;
                    repaint();
                }

                @Override
                public void mouseClicked(java.awt.event.MouseEvent event) {
                    if (action != null) {
                        action.run();
                    }
                }
            });
        }

        void setAction(Runnable action) {
            this.action = action;
        }

        void applyIcon(String iconKey) {
            this.iconKey = iconKey;
            repaint();
        }

        void setSelected(boolean value) {
            this.selected = value;
            repaint();
        }

        String label() {
            return label;
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            int width = getWidth();
            int height = getHeight();

            Color background = selected
                    ? Palette.color(Role.SIDEBAR_ACTIVE_BG)
                    : hovered ? Palette.color(Role.SIDEBAR_HOVER_BG) : null;
            if (background != null) {
                UiGraphics.fillRoundRect(g, 0, 0, width, height, 9, background);
            }

            // Icon and label always share one exact color; the icon is
            // explicitly tinted with the text color rather than relying
            // on FlatSVGIcon's cached currentColor rendering.
            Color foreground = selected
                    ? Palette.color(Role.SIDEBAR_ACTIVE_FG)
                    : Palette.color(Role.SIDEBAR_FG);
            if (!foreground.equals(getForeground())) {
                setForeground(foreground);
            }

            if (selected) {
                g.setColor(Palette.color(Role.SIDEBAR_ACTIVE_FG));
                g.fillRoundRect(0, height / 2 - 11, 3, 22, 3, 3);
            }

            if (iconKey != null) {
                javax.swing.Icon current = IconLoader.tinted(iconKey, 20, foreground);
                // Collapsed rail: center the icon between the left edge and
                // the scrollbar; expanded: keep the aligned 20px column.
                int iconX = width < COLLAPSED_WIDTH
                        ? (width - current.getIconWidth()) / 2
                        : 20;
                current.paintIcon(this, g, iconX, (height - current.getIconHeight()) / 2);
            }

            boolean showText = labelsVisible && width > COLLAPSED_WIDTH + 40 && label != null;
            if (showText) {
                g.setColor(foreground);
                g.setFont(g.getFont().deriveFont(Font.PLAIN, 13f));
                java.awt.FontMetrics metrics = g.getFontMetrics();
                int textY = (height + metrics.getAscent() - metrics.getDescent()) / 2;
                g.drawString(label, 54, textY);
            }
            g.dispose();
        }
    }
}
