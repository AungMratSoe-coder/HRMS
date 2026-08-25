package com.ams.hrms.ui.notification;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Font;
import java.time.format.DateTimeFormatter;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JToggleButton;
import javax.swing.WindowConstants;
import javax.swing.table.DefaultTableCellRenderer;

import com.ams.hrms.component.EmptyStatePanel;
import com.ams.hrms.component.HrmsTable;
import com.ams.hrms.component.ModernButton;
import com.ams.hrms.config.ServiceRegistry;
import com.ams.hrms.controller.NotificationController;
import com.ams.hrms.model.Notification;

import net.miginfocom.swing.MigLayout;

/**
 * Notification center (spec section 41): a modal feed of the signed-in
 * user's notifications with All/Unread filtering, bold unread rows and
 * mark-read actions. Every mutation invokes {@code onChange} so the caller
 * can refresh its unread badge.
 */
public class NotificationsDialog extends JDialog {

    private static final DateTimeFormatter RECEIVED_FORMAT =
            DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm");

    private final NotificationController controller =
            new NotificationController(ServiceRegistry.notificationService());
    private final Runnable onChange;

    private final JToggleButton allFilter = new JToggleButton("All", true);
    private final JToggleButton unreadFilter = new JToggleButton("Unread");
    private final ModernButton markReadButton = new ModernButton("Mark as Read", "check");
    private final ModernButton markAllButton = new ModernButton("Mark All Read", "check",
            ModernButton.Variant.GHOST);
    private final ModernButton closeButton = new ModernButton("Close", ModernButton.Variant.OUTLINE);

    private final CardLayout listCards = new CardLayout();
    private final JPanel listContainer = new JPanel(listCards);
    private final EmptyStatePanel emptyState = new EmptyStatePanel("bell",
            "No notifications here", "Approvals, alerts and reminders will appear in this feed.");
    private HrmsTable table;

    public NotificationsDialog(java.awt.Window owner, Runnable onChange) {
        super(owner, "Notifications", ModalityType.APPLICATION_MODAL);
        this.onChange = onChange;

        setLayout(new BorderLayout());
        setSize(760, 540);
        setMinimumSize(new java.awt.Dimension(620, 420));
        setLocationRelativeTo(owner);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        add(buildFilterBar(), BorderLayout.NORTH);
        add(buildListArea(), BorderLayout.CENTER);
        add(buildActionBar(), BorderLayout.SOUTH);

        wireActions();
        loadFeed();
    }

    // ------------------------------------------------------------------
    // Construction
    // ------------------------------------------------------------------

    private JPanel buildFilterBar() {
        JPanel bar = new JPanel(new MigLayout(
                "insets 16 20 12 20, aligny center",
                "[][grow][]8[]"));
        bar.setOpaque(false);

        allFilter.setFocusPainted(false);
        unreadFilter.setFocusPainted(false);
        ButtonGroup group = new ButtonGroup();
        group.add(allFilter);
        group.add(unreadFilter);

        java.awt.event.ItemListener filterListener = event -> {
            if (event.getStateChange() == java.awt.event.ItemEvent.SELECTED) {
                loadFeed();
            }
        };
        allFilter.addItemListener(filterListener);
        unreadFilter.addItemListener(filterListener);

        bar.add(new javax.swing.JLabel("Feed"), "growx");
        bar.add(allFilter);
        bar.add(unreadFilter);
        return bar;
    }

    private JPanel buildListArea() {
        table = HrmsTable.builder("Type", "Title", "Message", "Received")
                .fixedColumn(0, 110)
                .fixedColumn(3, 150)
                .hiddenColumn(4)   // id
                .hiddenColumn(5)   // unread flag
                .badgeColumn(0)
                .onDoubleClick((viewRow, modelRow) -> markSelectedRead())
                .build();
        table.setDefaultRenderer(Object.class, new UnreadHighlightRenderer());

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(16);

        listContainer.setLayout(listCards);
        listContainer.setOpaque(false);
        listContainer.add(scroll, "list");
        listContainer.add(emptyState, "empty");
        return listContainer;
    }

    private JPanel buildActionBar() {
        JPanel bar = new JPanel(new MigLayout(
                "insets 12 20 16 20, aligny center",
                "[][grow][]"));
        bar.setOpaque(false);

        markAllButton.setToolTipText("Mark every notification as read");
        closeButton.setToolTipText("Close the notification center");

        bar.add(markReadButton);
        bar.add(new javax.swing.JLabel(""), "growx");
        bar.add(markAllButton, "gapright 8");
        bar.add(closeButton);
        return bar;
    }

    private void wireActions() {
        allFilter.setSelected(true);
        markReadButton.setEnabled(false);
        table.getSelectionModel().addListSelectionListener(
                event -> markReadButton.setEnabled(selectedUnread()));

        markReadButton.addActionListener(event -> markSelectedRead());
        markAllButton.addActionListener(event -> controller.markAllRead(() -> {
            onChange.run();
            loadFeed();
        }));
        closeButton.addActionListener(event -> dispose());
    }

    // ------------------------------------------------------------------
    // Data
    // ------------------------------------------------------------------

    private void loadFeed() {
        boolean onlyUnread = unreadFilter.isSelected();
        controller.load(onlyUnread, notifications -> render(notifications));
    }

    private void render(List<Notification> notifications) {
        List<Object[]> rows = new java.util.ArrayList<>();
        for (Notification notification : notifications) {
            rows.add(new Object[]{
                    notification.getType(),
                    notification.getTitle(),
                    notification.getMessage(),
                    notification.getCreatedAt() == null
                            ? "-"
                            : RECEIVED_FORMAT.format(notification.getCreatedAt()),
                    notification.getId(),
                    notification.isUnread()});
        }
        table.setRows(rows);
        markReadButton.setEnabled(false);
        listCards.show(listContainer, rows.isEmpty() ? "empty" : "list");
    }

    private boolean selectedUnread() {
        int modelRow = table.selectedModelRow();
        if (modelRow < 0) {
            return false;
        }
        Object unread = table.getModel().getValueAt(modelRow, 5);
        return Boolean.TRUE.equals(unread);
    }

    private Long selectedId() {
        int modelRow = table.selectedModelRow();
        return modelRow < 0 ? null : (Long) table.getModel().getValueAt(modelRow, 4);
    }

    private void markSelectedRead() {
        Long id = selectedId();
        if (id == null || !selectedUnread()) {
            return;
        }
        controller.markRead(id,
                () -> {
                    onChange.run();
                    loadFeed();
                },
                () -> {
                    // Already read elsewhere; just resync the view.
                    onChange.run();
                    loadFeed();
                });
    }

    /** Renders unread rows in bold while keeping the standard cell padding. */
    private static final class UnreadHighlightRenderer extends DefaultTableCellRenderer {

        UnreadHighlightRenderer() {
            setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 12));
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus,
                                                       int row, int column) {
            Component component = super.getTableCellRendererComponent(
                    table, value, isSelected, hasFocus, row, column);
            int modelRow = table.convertRowIndexToModel(row);
            boolean unread = modelRow >= 0
                    && Boolean.TRUE.equals(table.getModel().getValueAt(modelRow, 5));

            Font baseFont = table.getFont();
            component.setFont(unread ? baseFont.deriveFont(Font.BOLD) : baseFont);
            if (!isSelected) {
                component.setForeground(com.ams.hrms.ui.theme.Palette.color(
                        com.ams.hrms.ui.theme.Palette.Role.TEXT));
            }
            setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 12));
            return component;
        }
    }
}
