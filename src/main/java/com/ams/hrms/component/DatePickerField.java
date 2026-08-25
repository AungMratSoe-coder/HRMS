package com.ams.hrms.component;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTextField;
import javax.swing.SwingConstants;

import com.ams.hrms.ui.theme.Palette;
import com.ams.hrms.ui.theme.Palette.Role;
import com.ams.hrms.util.UiGraphics;

import net.miginfocom.swing.MigLayout;

/**
 * Date editor: text field (ISO YYYY-MM-DD) plus a calendar popup button
 * (spec section 36). Typing is validated live via FlatLaf's error outline;
 * the month grid supports navigation and click-to-select.
 */
public class DatePickerField extends JPanel {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final String[] WEEKDAYS = {"Mo", "Tu", "We", "Th", "Fr", "Sa", "Su"};

    private final JTextField editor = new JTextField(10);
    private final JPopupMenu calendarPopup = new JPopupMenu();
    private MonthView monthView;
    private final javax.swing.event.EventListenerList dateListeners =
            new javax.swing.event.EventListenerList();
    private LocalDate lastNotified;

    private LocalDate value;

    /** Registers a listener fired whenever the selected date changes. */
    public void addDateChangedListener(java.awt.event.ActionListener listener) {
        dateListeners.add(java.awt.event.ActionListener.class, listener);
    }

    private void fireDateChanged() {
        LocalDate current = getDate();
        if (current == null ? lastNotified == null : current.equals(lastNotified)) {
            return;
        }
        lastNotified = current;
        for (var listener : dateListeners.getListeners(java.awt.event.ActionListener.class)) {
            listener.actionPerformed(new java.awt.event.ActionEvent(
                    this, java.awt.event.ActionEvent.ACTION_FIRST, "dateChanged"));
        }
    }

    public DatePickerField() {
        setOpaque(false);
        setLayout(new net.miginfocom.swing.MigLayout("insets 0, gap 4", "[grow,fill][]", "[fill]"));        editor.setHorizontalAlignment(SwingConstants.CENTER);
        editor.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                validateTypedDate();
            }

            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                validateTypedDate();
            }

            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                validateTypedDate();
            }
        });

        javax.swing.JButton openButton = ModernButton.iconOnly("calendar", "Open calendar");
        openButton.addActionListener(event -> showCalendar());

        add(editor);
        add(openButton);

        monthView = new MonthView(LocalDate.now());
        calendarPopup.add(monthView);
    }

    /** Returns the selected date, or null when empty/invalid. */
    public LocalDate getDate() {
        String raw = editor.getText().trim();
        if (raw.isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(raw, ISO);
        } catch (Exception e) {
            return null;
        }
    }

    /** Re-resolves cached palette colors after a theme switch. */
    @Override
    public void updateUI() {
        super.updateUI();
        if (monthView != null) {
            monthView.refreshTheme();
        }
    }

    public void setDate(LocalDate date) {
        this.value = date;
        editor.setText(date == null ? "" : date.format(ISO));
        clearErrorOutline();
        fireDateChanged();
    }

    public JTextField editorComponent() {
        return editor;
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    private void validateTypedDate() {
        String raw = editor.getText().trim();
        if (raw.isEmpty()) {
            clearErrorOutline();
            fireDateChanged();
            return;
        }
        try {
            this.value = LocalDate.parse(raw, ISO);
            clearErrorOutline();
            fireDateChanged();
        } catch (Exception e) {
            markErrorOutline();
        }
    }

    private void markErrorOutline() {
        editor.putClientProperty("JComponent.outline", "error");
    }

    private void clearErrorOutline() {
        editor.putClientProperty("JComponent.outline", null);
    }

    private void showCalendar() {
        LocalDate anchor = getDate() != null ? getDate() : LocalDate.now();
        monthView.showMonth(anchor);
        calendarPopup.show(editor, 0, editor.getHeight());
    }

    /**
     * One-month grid: header with navigation, weekday row and six weeks of
     * day cells.
     */
    private final class MonthView extends JPanel {

        private YearMonth displayedMonth;
        private final JLabel monthTitle = new JLabel("", SwingConstants.CENTER);
        private final JPanel dayGrid = new JPanel(new GridLayout(0, 7, 2, 2));
        private final java.util.List<JLabel> weekdayLabels = new java.util.ArrayList<>();

        MonthView(LocalDate anchor) {
            setOpaque(true);
            setBackground(Palette.color(Role.CARD_BG));
            setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
            setLayout(new net.miginfocom.swing.MigLayout(
                    "wrap 7, insets 4, gap 2",
                    "[36!,center][36!,center][36!,center][36!,center][36!,center][36!,center][36!,center]",
                    "[][][]"));

            javax.swing.JButton previous = ModernButton.iconOnly("chevron-left", "Previous month");
            javax.swing.JButton next = ModernButton.iconOnly("chevron-right", "Next month");
            previous.addActionListener(e -> shiftMonth(-1));
            next.addActionListener(e -> shiftMonth(1));

            monthTitle.setFont(monthTitle.getFont().deriveFont(Font.BOLD, 13f));
            monthTitle.setForeground(Palette.color(Role.TEXT));

            add(previous);
            add(monthTitle, "span 5, growx");
            add(next);

            for (String weekday : WEEKDAYS) {
                JLabel label = new JLabel(weekday, SwingConstants.CENTER);
                label.setFont(label.getFont().deriveFont(Font.PLAIN, 11f));
                label.setForeground(Palette.color(Role.TEXT_MUTED));
                weekdayLabels.add(label);
                add(label);
            }
            add(dayGrid, "span 7, growx");

            displayedMonth = YearMonth.from(anchor);
            rebuildGrid();
        }

        /** Re-resolves cached palette colors after a theme switch. */
        void refreshTheme() {
            setBackground(Palette.color(Role.CARD_BG));
            monthTitle.setForeground(Palette.color(Role.TEXT));
            for (JLabel label : weekdayLabels) {
                label.setForeground(Palette.color(Role.TEXT_MUTED));
            }
            repaint();
        }

        void showMonth(LocalDate anchor) {
            displayedMonth = YearMonth.from(anchor);
            rebuildGrid();
        }

        private void shiftMonth(int months) {
            displayedMonth = displayedMonth.plusMonths(months);
            rebuildGrid();
        }

        private void rebuildGrid() {
            monthTitle.setText(displayedMonth.getMonth()
                    .getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH)
                    + " " + displayedMonth.getYear());
            dayGrid.removeAll();

            LocalDate firstOfMonth = displayedMonth.atDay(1);
            int leadingBlanks = (firstOfMonth.getDayOfWeek().getValue() + 6) % 7; // Monday-first

            for (int i = 0; i < leadingBlanks; i++) {
                dayGrid.add(new JLabel());
            }
            LocalDate today = LocalDate.now();
            for (int day = 1; day <= displayedMonth.lengthOfMonth(); day++) {
                LocalDate cellDate = displayedMonth.atDay(day);
                DayCell cell = new DayCell(cellDate,
                        cellDate.equals(value),
                        cellDate.equals(today));
                final LocalDate selection = cellDate;
                cell.addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseClicked(MouseEvent event) {
                        setDate(selection);
                        calendarPopup.setVisible(false);
                    }
                });
                dayGrid.add(cell);
            }
            int trailing = (7 - (leadingBlanks + displayedMonth.lengthOfMonth()) % 7) % 7;
            for (int i = 0; i < trailing; i++) {
                dayGrid.add(new JLabel());
            }
            revalidate();
            repaint();
        }
    }

    /** A single selectable day square. */
    private static final class DayCell extends JLabel {

        private static final int CELL = 30;

        DayCell(LocalDate date, boolean selected, boolean today) {
            super(String.valueOf(date.getDayOfMonth()), SwingConstants.CENTER);
            setOpaque(false);
            setFont(getFont().deriveFont(selected ? Font.BOLD : Font.PLAIN, 12f));
            setToolTipText(today ? "Today" : null);
            putClientProperty("date", date);
            putClientProperty("selected", selected);
            putClientProperty("today", today);
            setPreferredSize(new java.awt.Dimension(CELL, CELL));
            setHorizontalAlignment(SwingConstants.CENTER);

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent event) {
                    if (!selected) {
                        setForeground(Palette.color(Role.ACCENT));
                        repaint();
                    }
                }

                @Override
                public void mouseExited(MouseEvent event) {
                    if (!selected && !today) {
                        setForeground(Palette.color(Role.TEXT));
                        repaint();
                    }
                }
            });
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            boolean selected = Boolean.TRUE.equals(getClientProperty("selected"));
            boolean todayFlag = Boolean.TRUE.equals(getClientProperty("today"));

            if (selected) {
                UiGraphics.fillRoundRect(g, 3, 3, getWidth() - 6, getHeight() - 6, 8,
                        Palette.color(Role.ACCENT));
                setForeground(Palette.readableForeground(Palette.color(Role.ACCENT)));
            } else if (todayFlag) {
                UiGraphics.drawRoundRect(g, 3, 3, getWidth() - 6, getHeight() - 6, 8,
                        Palette.color(Role.ACCENT));
                setForeground(Palette.color(Role.ACCENT));
            } else {
                setForeground(Palette.color(Role.TEXT));
            }
            g.dispose();
            super.paintComponent(graphics);
        }
    }
}
