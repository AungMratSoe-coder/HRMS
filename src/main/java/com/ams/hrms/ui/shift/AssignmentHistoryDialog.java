package com.ams.hrms.ui.shift;

import java.awt.BorderLayout;
import java.util.List;

import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import com.ams.hrms.component.HrmsTable;
import com.ams.hrms.model.EmployeeShift;

/** Read-only shift assignment history for one employee (spec section 17). */
public class AssignmentHistoryDialog extends JDialog {

    public AssignmentHistoryDialog(java.awt.Window owner,
                                   String employeeLabel,
                                   List<EmployeeShift> assignments) {
        super(owner, "Shift History - " + employeeLabel, ModalityType.APPLICATION_MODAL);
        setLayout(new BorderLayout());
        setSize(680, 400);
        setLocationRelativeTo(owner);

        HrmsTable table = HrmsTable.builder("Shift", "From", "Until", "Assigned By")
                .fixedColumn(0, 180)
                .fixedColumn(1, 110)
                .build();

        List<Object[]> rows = new java.util.ArrayList<>();
        for (EmployeeShift assignment : assignments) {
            rows.add(new Object[]{
                    assignment.getShiftName(),
                    assignment.getEffectiveFrom().toString(),
                    assignment.isCurrent() ? "Current" : assignment.getEffectiveTo().toString(),
                    assignment.getAssignedByName() == null ? "-" : assignment.getAssignedByName()});
        }
        table.setRows(rows);

        JPanel content = new JPanel(new BorderLayout());
        content.setOpaque(false);
        content.add(new JScrollPane(table), BorderLayout.CENTER);
        add(content);
    }
}
