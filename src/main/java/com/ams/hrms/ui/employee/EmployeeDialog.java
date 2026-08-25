package com.ams.hrms.ui.employee;

import java.awt.BorderLayout;
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;

import com.ams.hrms.component.FormField;
import com.ams.hrms.component.ModernButton;
import com.ams.hrms.config.ServiceRegistry;
import com.ams.hrms.controller.EmployeeController;
import com.ams.hrms.exception.ErrorHandler;
import com.ams.hrms.exception.HrmsException;
import com.ams.hrms.exception.ValidationException;
import com.ams.hrms.model.Department;
import com.ams.hrms.model.Employee;
import com.ams.hrms.model.Position;
import com.ams.hrms.ui.theme.Palette;
import com.ams.hrms.ui.theme.Palette.Role;
import com.ams.hrms.util.IconLoader;

import net.miginfocom.swing.MigLayout;

/**
 * Create/Edit dialog for employees (spec section 10). The position picker
 * cascades from the selected department; saving runs off the EDT and closes
 * only on success.
 */
public class EmployeeDialog extends JDialog {

    public enum Result {
        SAVED, CANCELLED
    }

    private static final String[] GENDERS = {"MALE", "FEMALE", "OTHER"};
    private static final String[] EMPLOYMENT_TYPES =
            {"FULL_TIME", "PART_TIME", "CONTRACT", "INTERN", "PROBATION"};

    private final EmployeeController controller =
            new EmployeeController(ServiceRegistry.employeeService());

    /** Manager picker option supplied by the owning panel. */
    public record ManagerOption(long id, String display) {
    }

    private final Employee employee;
    private final boolean isNew;
    private final List<Department> activeDepartments = new ArrayList<>();
    private final List<Position> activePositions = new ArrayList<>();
    private final List<ManagerOption> managerOptions = new ArrayList<>();

    private FormField codeField;
    private FormField firstNameField;
    private FormField lastNameField;
    private FormField genderField;
    private FormField dobField;
    private FormField nrcField;
    private FormField phoneField;
    private FormField emailField;
    private FormField addressField;
    private FormField joinDateField;
    private FormField employmentTypeField;
    private FormField departmentField;
    private FormField positionField;
    private FormField managerField;
    private FormField salaryField;
    private javax.swing.JComboBox<String> statusCombo;
    private final JLabel errorBanner = new JLabel();
    private final JButton saveButton = new ModernButton("Save", "check");
    private Result result = Result.CANCELLED;

    public EmployeeDialog(java.awt.Window owner, Employee existing,
                          List<Department> allDepartments, List<Position> allActivePositions,
                          List<ManagerOption> managers) {
        super(owner, existing == null ? "New Employee" : "Edit Employee",
                ModalityType.APPLICATION_MODAL);
        this.employee = existing == null ? new Employee() : existing;
        this.isNew = existing == null;
        for (Department department : allDepartments) {
            if ("ACTIVE".equals(department.getStatus())) {
                activeDepartments.add(department);
            }
        }
        for (Position position : allActivePositions) {
            if ("ACTIVE".equals(position.getStatus())) {
                activePositions.add(position);
            }
        }
        managerOptions.addAll(managers);

        setLayout(new BorderLayout());
        add(buildForm(), BorderLayout.CENTER);
        add(buildButtons(), BorderLayout.SOUTH);

        setSize(780, 700);
        setLocationRelativeTo(owner);
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        getRootPane().setDefaultButton(saveButton);
    }

    // ------------------------------------------------------------------
    // Form
    // ------------------------------------------------------------------

    private JPanel buildForm() {
        JPanel form = new JPanel(new MigLayout(
                "wrap 4, insets 24 28 12 28, gapx 16, gapy 8",
                "[grow,fill][grow,fill][grow,fill][grow,fill]"));

        JLabel titleLabel = new JLabel(isNew ? "Create employee" : "Edit employee");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 16f));
        titleLabel.setForeground(Palette.color(Role.TEXT));
        form.add(titleLabel, "span 4, wrap unrelated");

        codeField = FormField.textField("Employee Code *", true);
        genderField = FormField.comboBox("Gender *", GENDERS, true);
        form.add(codeField);
        form.add(genderField, "span 3, growx");

        firstNameField = FormField.textField("First Name *", true);
        lastNameField = FormField.textField("Last Name *", true);
        dobField = FormField.datePicker("Date of Birth", false);
        form.add(firstNameField);
        form.add(lastNameField);
        form.add(dobField, "span 2, growx");

        nrcField = FormField.textField("NRC / National ID", false);
        phoneField = FormField.textField("Phone", false);
        emailField = FormField.textField("Email", false);
        form.add(nrcField);
        form.add(phoneField);
        form.add(emailField, "span 2, growx");

        addressField = FormField.textArea("Address", false);
        form.add(addressField, "span 4, height 60!");

        joinDateField = FormField.datePicker("Join Date *", true);
        employmentTypeField = FormField.comboBox("Employment Type *", EMPLOYMENT_TYPES, true);
        departmentField = FormField.custom("Department *", true, buildDepartmentCombo());
        positionField = FormField.custom("Position *", true, buildPositionCombo());
        managerField = FormField.custom("Manager", false, buildManagerCombo());
        salaryField = FormField.textField("Basic Salary *", true);

        form.add(joinDateField);
        form.add(employmentTypeField);
        form.add(departmentField);
        form.add(positionField);
        form.add(managerField);
        form.add(salaryField, "span 3, growx");

        if (!isNew) {
            statusCombo = new javax.swing.JComboBox<>(new String[]{"ACTIVE", "INACTIVE"});
            statusCombo.setSelectedItem(employee.getStatus());
            form.add(wrapLabeled("Status", statusCombo), "width 200!, alignx left");
        }

        errorBanner.setIcon(IconLoader.small("warning"));
        errorBanner.setForeground(Palette.color(Role.DANGER));
        errorBanner.setVerticalTextPosition(javax.swing.SwingConstants.TOP);
        errorBanner.setVisible(false);
        form.add(errorBanner, "span 4");

        fillFromEmployee();
        wireDepartmentCascade();
        return form;
    }

    private javax.swing.JComboBox<String> buildDepartmentCombo() {
        List<String> displays = new ArrayList<>();
        for (Department department : activeDepartments) {
            displays.add(department.getCode() + " - " + department.getName());
        }
        return new javax.swing.JComboBox<>(displays.toArray(new String[0]));
    }

    private javax.swing.JComboBox<String> buildPositionCombo() {
        return new javax.swing.JComboBox<>(new String[]{"- select department -"});
    }

    private javax.swing.JComboBox<String> buildManagerCombo() {
        List<String> displays = new ArrayList<>();
        displays.add("- None -");
        for (var option : managerOptions) {
            displays.add(option.display());
        }
        return new javax.swing.JComboBox<>(displays.toArray(new String[0]));
    }

    @SuppressWarnings("unchecked")
    private void fillFromEmployee() {
        if (isNew) {
            joinDateField.setDate(java.time.LocalDate.now());
            wireDepartmentCascade();
            return;
        }
        codeField.setText(employee.getCode());
        firstNameField.setText(employee.getFirstName());
        lastNameField.setText(employee.getLastName());
        genderField.setText(employee.getGender());
        dobField.setDate(employee.getDateOfBirth());
        nrcField.setText(employee.getNrc());
        phoneField.setText(employee.getPhone());
        emailField.setText(employee.getEmail());
        addressField.setText(employee.getAddress());
        employmentTypeField.setText(employee.getEmploymentType());

        // Select department (fires the cascade), then restore its position.
        selectDepartment(employee.getDepartmentId());
        rebuildPositionChoices(employee.getDepartmentId(), employee.getPositionId());

        javax.swing.JComboBox<String> managerCombo =
                (javax.swing.JComboBox<String>) managerField.editor();
        if (employee.getManagerId() != null) {
            for (int i = 0; i < managerOptions.size(); i++) {
                if (managerOptions.get(i).id() == employee.getManagerId()) {
                    managerCombo.setSelectedIndex(i + 1);
                    break;
                }
            }
        } else {
            managerCombo.setSelectedIndex(0);
        }
        salaryField.setText(employee.getBasicSalary() == null
                ? "" : employee.getBasicSalary().toPlainString());
        wireDepartmentCascade(); // attach user-interaction listener AFTER initial fill
    }

    @SuppressWarnings("unchecked")
    private void wireDepartmentCascade() {
        javax.swing.JComboBox<String> deptCombo = (javax.swing.JComboBox<String>) departmentField.editor();
        for (var listener : deptCombo.getActionListeners()) {
            deptCombo.removeActionListener(listener);
        }
        deptCombo.addActionListener(event -> rebuildPositionChoices(selectedDepartmentId(), null));
    }

    @SuppressWarnings("unchecked")
    private void selectDepartment(Long departmentId) {
        javax.swing.JComboBox<String> deptCombo = (javax.swing.JComboBox<String>) departmentField.editor();
        deptCombo.setSelectedIndex(-1);
        if (departmentId != null) {
            for (int i = 0; i < activeDepartments.size(); i++) {
                if (activeDepartments.get(i).getId().equals(departmentId)) {
                    deptCombo.setSelectedIndex(i);
                    return;
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void rebuildPositionChoices(Long departmentId, Long keepPositionId) {
        javax.swing.JComboBox<String> posCombo = (javax.swing.JComboBox<String>) positionField.editor();
        posCombo.removeAllItems();
        int restoreAt = -1;
        int visible = 0;
        for (Position position : activePositions) {
            if (departmentId != null && departmentId.equals(position.getDepartmentId())) {
                posCombo.addItem(position.getCode() + " - " + position.getName());
                if (keepPositionId != null && position.getId().equals(keepPositionId)) {
                    restoreAt = visible;
                }
                visible++;
            }
        }
        if (restoreAt >= 0) {
            posCombo.setSelectedIndex(restoreAt);
        } else if (posCombo.getItemCount() > 0) {
            posCombo.setSelectedIndex(-1);
        }
    }

    @SuppressWarnings("unchecked")
    private Long selectedDepartmentId() {
        int selected = ((javax.swing.JComboBox<String>) departmentField.editor()).getSelectedIndex();
        return selected < 0 ? null : activeDepartments.get(selected).getId();
    }

    private JPanel wrapLabeled(String label, javax.swing.JComponent editor) {
        JPanel wrapper = new JPanel(new MigLayout("wrap 1, insets 0, gap 3", "[grow,fill]", "[][]"));
        wrapper.setOpaque(false);
        JLabel fieldLabel = new JLabel(label);
        fieldLabel.setFont(fieldLabel.getFont().deriveFont(Font.PLAIN, 12f));
        fieldLabel.setForeground(Palette.color(Role.TEXT_MUTED));
        wrapper.add(fieldLabel);
        wrapper.add(editor, "height 30!");
        return wrapper;
    }

    private JPanel buildButtons() {
        JPanel buttons = new JPanel(new MigLayout("insets 14 28, gap 10, alignx right", "push[][]"));
        buttons.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Palette.color(Role.CARD_BORDER)));

        JButton cancel = new ModernButton("Cancel", ModernButton.Variant.OUTLINE);
        cancel.addActionListener(event -> dispose());
        saveButton.addActionListener(event -> submit());
        buttons.add(cancel);
        buttons.add(saveButton);
        return buttons;
    }

    // ------------------------------------------------------------------
    // Submit
    // ------------------------------------------------------------------

    private void submit() {
        clearErrors();
        List<String> localErrors = new ArrayList<>();

        java.math.BigDecimal salary = com.ams.hrms.validator.Validators.parseMoney(
                localErrors, salaryField.getText(), "Basic salary");
        java.time.LocalDate dob = dobField.getDate();
        java.time.LocalDate join = joinDateField.getDate();
        if (dob != null && join != null && !dob.isBefore(join)) {
            localErrors.add("Date of birth must be before the join date.");
        }
        Long deptId = selectedDepartmentId();
        int posIndex = ((javax.swing.JComboBox<?>) positionField.editor()).getSelectedIndex();
        int mgrIndex = ((javax.swing.JComboBox<?>) managerField.editor()).getSelectedIndex();

        if (deptId == null) {
            localErrors.add("Department is required.");
        }
        if (posIndex < 0) {
            localErrors.add("Position is required.");
        }
        if (!localErrors.isEmpty()) {
            showError(String.join(" ", localErrors));
            return;
        }

        employee.setCode(codeField.getText());
        employee.setFirstName(firstNameField.getText());
        employee.setLastName(lastNameField.getText());
        employee.setGender(String.valueOf(((javax.swing.JComboBox<?>) genderField.editor())
                .getSelectedItem()));
        employee.setDateOfBirth(dob);
        employee.setNrc(nrcField.getText());
        employee.setPhone(phoneField.getText());
        employee.setEmail(emailField.getText());
        employee.setAddress(addressField.getText());
        employee.setJoinDate(join);
        employee.setEmploymentType(String.valueOf(((javax.swing.JComboBox<?>) employmentTypeField
                .editor()).getSelectedItem()));
        employee.setDepartmentId(deptId);
        employee.setPositionId(visiblePositionIds().get(posIndex));
        employee.setManagerId(mgrIndex <= 0 ? null : managerOptions.get(mgrIndex - 1).id());
        employee.setBasicSalary(salary);
        if (!isNew && statusCombo != null) {
            employee.setStatus(String.valueOf(statusCombo.getSelectedItem()));
        }

        saveButton.setEnabled(false);
        controller.save(employee,
                id -> {
                    employee.setId(id);
                    result = Result.SAVED;
                    dispose();
                },
                error -> {
                    saveButton.setEnabled(true);
                    if (error instanceof ValidationException ve) {
                        showError(String.join(" ", ve.getErrors()));
                    } else if (error instanceof HrmsException he) {
                        showError(he.getUserMessage());
                    } else {
                        ErrorHandler.handle(error);
                    }
                });
    }

    private List<Long> visiblePositionIds() {
        List<Long> ids = new ArrayList<>();
        Long deptId = selectedDepartmentId();
        for (Position position : activePositions) {
            if (deptId != null && deptId.equals(position.getDepartmentId())) {
                ids.add(position.getId());
            }
        }
        return ids;
    }

    private void clearErrors() {
        errorBanner.setVisible(false);
    }

    private void showError(String message) {
        errorBanner.setText(message);
        errorBanner.setVisible(true);
        revalidate();
    }

    // ------------------------------------------------------------------
    // API
    // ------------------------------------------------------------------

    public Result showDialog() {
        setVisible(true);
        return result;
    }

    public Employee employee() {
        return employee;
    }
}
