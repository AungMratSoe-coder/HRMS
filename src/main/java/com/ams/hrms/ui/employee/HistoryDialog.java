package com.ams.hrms.ui.employee;

import java.awt.BorderLayout;
import java.util.List;

import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import com.ams.hrms.component.HrmsTable;
import com.ams.hrms.repository.EmployeeRepository.HistoryEntry;

/**
 * Read-only employment history for one employee (spec sections 10/26).
 * Entries are append-only; this dialog never edits them.
 */
public class HistoryDialog extends JDialog {

    public HistoryDialog(java.awt.Window owner, List<HistoryEntry> entries, String employeeLabel) {
        super(owner, "Employment History - " + employeeLabel, ModalityType.APPLICATION_MODAL);
        setLayout(new BorderLayout());
        setSize(680, 420);
        setLocationRelativeTo(owner);

        HrmsTable table = HrmsTable.builder("Date", "Change", "From", "To", "Remarks")
                .fixedColumn(0, 110)
                .fixedColumn(1, 150)
                .build();

        List<Object[]> rows = new java.util.ArrayList<>();
        for (HistoryEntry entry : entries) {
            rows.add(new Object[]{
                    entry.effectiveDate().toString(),
                    entry.changeType(),
                    entry.oldValue() == null ? "-" : entry.oldValue(),
                    entry.newValue() == null ? "-" : entry.newValue(),
                    entry.remarks() == null ? "" : entry.remarks()});
        }
        table.setRows(rows);

        JPanel content = new JPanel(new BorderLayout());
        content.setOpaque(false);
        content.add(new JScrollPane(table), BorderLayout.CENTER);
        add(content);
    }
}
