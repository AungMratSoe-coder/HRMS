package com.ams.hrms.component;

import java.awt.FlowLayout;
import java.util.function.IntConsumer;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JLabel;

import com.ams.hrms.ui.theme.Palette;
import com.ams.hrms.ui.theme.Palette.Role;
import com.ams.hrms.util.IconLoader;

import net.miginfocom.swing.MigLayout;

/**
 * Table pagination footer (spec sections 36 and 37): first/previous/next/last
 * controls, "page x of y" label and a rows-per-page selector. Wire it to the
 * table's data source via {@link #onPageChange(IntConsumer)} and feed it the
 * total row count with {@link #updateTotal(int)}.
 */
public class PaginationPanel extends javax.swing.JPanel {

    private static final int[] PAGE_SIZES = {10, 25, 50, 100};

    private final JLabel pageInfoLabel = new JLabel();
    private final JComboBox<String> pageSizeCombo =
            new JComboBox<>(new DefaultComboBoxModel<>(new String[]{"10", "25", "50", "100"}));

    private IntConsumer pageChangeListener;
    private IntConsumer pageSizeChangeListener;

    private final javax.swing.JButton firstButton;
    private final javax.swing.JButton prevButton;
    private final javax.swing.JButton nextButton;
    private final javax.swing.JButton lastButton;

    private int currentPage = 1;
    private int totalPages = 1;
    private int pageSize = 25;

    public PaginationPanel() {
        setLayout(new MigLayout("insets 4 0, align center", "[][][][][]push[]", "[center]"));
        setOpaque(false);

        firstButton = ModernButton.iconOnly("chevron-left", "First page");
        prevButton = ModernButton.iconOnly("chevron-left", "Previous page");
        nextButton = ModernButton.iconOnly("chevron-right", "Next page");
        lastButton = ModernButton.iconOnly("chevron-right", "Last page");

        firstButton.addActionListener(e -> goToPage(1));
        prevButton.addActionListener(e -> goToPage(currentPage - 1));
        nextButton.addActionListener(e -> goToPage(currentPage + 1));
        lastButton.addActionListener(e -> goToPage(totalPages));
        pageSizeCombo.setSelectedItem("25");
        pageSizeCombo.addActionListener(e -> changePageSize());

        pageInfoLabel.setForeground(Palette.color(Role.TEXT_MUTED));

        add(firstButton);
        add(prevButton, "gap 2");
        add(pageInfoLabel, "gap 10, gapright 10");
        add(nextButton, "gap 2");
        add(lastButton);
        add(new JLabel("Rows:"), "gap 24");
        add(pageSizeCombo);
        refreshControls();
    }

    /** Re-resolves palette colors after a theme switch. */
    @Override
    public void updateUI() {
        super.updateUI();
        if (pageInfoLabel != null) {
            pageInfoLabel.setForeground(Palette.color(Role.TEXT_MUTED));
        }
    }

    /** Registers the callback fired when the user requests another page. */
    public void onPageChange(IntConsumer listener) {
        this.pageChangeListener = listener;
    }

    /** Registers the callback fired when the rows-per-page value changes. */
    public void onPageSizeChange(IntConsumer listener) {
        this.pageSizeChangeListener = listener;
    }

    /** Feeds the current total row count; recalculates pages and clamps state. */
    public void updateTotal(int totalRows) {
        this.totalPages = Math.max(1, (int) Math.ceil(totalRows / (double) pageSize));
        if (currentPage > totalPages) {
            currentPage = totalPages;
        }
        refreshControls();
    }

    public int currentPage() {
        return currentPage;
    }

    public int pageSize() {
        return pageSize;
    }

    public void reset() {
        currentPage = 1;
        refreshControls();
    }

    private void goToPage(int requestedPage) {
        int target = Math.max(1, Math.min(totalPages, requestedPage));
        if (target == currentPage) {
            refreshControls();
            return;
        }
        currentPage = target;
        refreshControls();
        if (pageChangeListener != null) {
            pageChangeListener.accept(currentPage);
        }
    }

    private void changePageSize() {
        int selected = Integer.parseInt((String) pageSizeCombo.getSelectedItem());
        if (selected == pageSize) {
            return;
        }
        pageSize = selected;
        currentPage = 1;
        refreshControls();
        if (pageSizeChangeListener != null) {
            pageSizeChangeListener.accept(pageSize);
        }
    }

    private void refreshControls() {
        pageInfoLabel.setText("Page " + currentPage + " of " + totalPages);
        boolean multi = totalPages > 1;
        firstButton.setEnabled(multi && currentPage > 1);
        prevButton.setEnabled(multi && currentPage > 1);
        nextButton.setEnabled(multi && currentPage < totalPages);
        lastButton.setEnabled(multi && currentPage < totalPages);
        revalidate();
        repaint();
    }
}
