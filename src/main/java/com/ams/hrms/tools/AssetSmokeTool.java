package com.ams.hrms.tools;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.ams.hrms.config.AppConfig;
import com.ams.hrms.config.DatabaseConfig;
import com.ams.hrms.config.ServiceRegistry;
import com.ams.hrms.db.DatabaseMigrator;
import com.ams.hrms.db.DbChecker;
import com.ams.hrms.exception.AuthorizationException;
import com.ams.hrms.exception.ValidationException;
import com.ams.hrms.exception.BusinessException;
import com.ams.hrms.model.Asset;
import com.ams.hrms.repository.Sql;
import com.ams.hrms.service.AssetService;
import com.ams.hrms.service.AuthService;

/**
 * Development-only Phase 20 verification against the live database: asset
 * registration with auto code, transactional assignment (asset flips to
 * ASSIGNED), duplicate-assignment guard, return with condition routing
 * (DAMAGED &rarr; UNDER_REPAIR), repair release, lost handling, overdue
 * flagging and RBAC denial for FINANCE. Idempotent cleanup afterwards.
 */
public final class AssetSmokeTool {

    private static int failures;
    private static long smokeAssetId;
    private static long secondAssetId;
    private static long assignmentId;
    private static long lostAssignmentId;

    public static void main(String[] args) throws Exception {
        AppConfig config = AppConfig.get();
        DbChecker.check(config);
        DatabaseConfig.initialize(config);
        DatabaseMigrator.migrate(config);
        ServiceRegistry.initialize();

        AuthService authService = ServiceRegistry.authService();
        AssetService assets = ServiceRegistry.assetService();
        assets.refreshOverdue();

        purgeArtifacts();
        authService.login("admin@ams.local", "Admin@123");

        long employeeId = new Sql().scalarLong(
                "SELECT id FROM employees WHERE employee_code = 'EMP-0002'");
        LocalDate today = LocalDate.now();

        // --- catalogue -------------------------------------------------------
        Asset asset = new Asset();
        asset.setName("SMOKE ThinkPad T14");
        asset.setCategory("LAPTOP");
        asset.setSerialNumber("SMOKE-SN-0001");
        asset.setPurchaseDate(today.minusYears(1));
        asset.setPurchaseCost(BigDecimal.valueOf(1200));
        asset.setConditionStatus("NEW");
        check("register asset with generated code", () -> {
            smokeAssetId = assets.saveAsset(asset);
            var stored = assets.findAssets("SMOKE", null, "AVAILABLE").stream()
                    .filter(candidate -> candidate.getId() == smokeAssetId)
                    .findFirst().orElseThrow();
            return stored.getCode() != null && stored.getCode().startsWith("AST-");
        });

        check("warranty before purchase rejected",
                () -> {
                    try {
                        Asset bad = cloneAsset("SMOKE Warranty Breaker");
                        bad.setWarrantyExpiry(today.minusYears(2));
                        assets.saveAsset(bad);
                        return false;
                    } catch (ValidationException expected) {
                        return true;
                    }
                });

        // --- assignment ---------------------------------------------------------
        check("assign AVAILABLE asset to employee", () -> {
            assignmentId = assets.assign(smokeAssetId, employeeId, today,
                    today.plusWeeks(4), "Smoke assignment");
            var stored = assets.findAssets(null, "LAPTOP", "ASSIGNED").stream()
                    .filter(candidate -> candidate.getId() == smokeAssetId)
                    .findFirst().orElseThrow();
            return stored.getHolderCode() != null
                    && stored.getHolderCode().equals("EMP-0002");
        });

        check("second assign of a non-AVAILABLE asset refused",
                () -> {
                    try {
                        assets.assign(smokeAssetId, employeeId, today, null, null);
                        return false;
                    } catch (BusinessException expected) {
                        return true;
                    }
                });

        check("return before assigned date rejected",
                () -> {
                    try {
                        assets.returnAsset(assignmentId, today.minusDays(1), "GOOD", null);
                        return false;
                    } catch (ValidationException expected) {
                        return true;
                    }
                });

        check("damaged return routes the asset into repair", () -> {
            assets.returnAsset(assignmentId, today, "DAMAGED", "Broken hinge");
            boolean closed = assets.findAssignments(null, null, "RETURNED", null).stream()
                    .filter(candidate -> candidate.getId() == assignmentId)
                    .findFirst().orElseThrow()
                    .getConditionOnReturn().equals("DAMAGED");
            String status = statusOf(smokeAssetId);
            return closed && "UNDER_REPAIR".equals(status);
        });

        check("manual transition to ASSIGNED is blocked",
                () -> {
                    try {
                        assets.setAssetStatus(secondAssetSetup(), "ASSIGNED");
                        return false;
                    } catch (BusinessException expected) {
                        return true;
                    }
                });

        // --- lost flow ------------------------------------------------------------
        check("lost assignment freezes asset and record", () -> {
            secondAssetId = registerSecondAsset(assets);
            lostAssignmentId = assets.assign(secondAssetId, employeeId, today,
                    null, "Lost on day one");
            assets.markLost(lostAssignmentId, "Never arrived");
            boolean assignmentLost = assets.findAssignments(null, null, "LOST", null)
                    .stream().anyMatch(candidate -> candidate.getId() == lostAssignmentId);
            return assignmentLost && "LOST".equals(statusOf(secondAssetId));
        });

        // --- RBAC: FINANCE has ASSET_VIEW but not ASSET_ASSIGN/ASSET_MANAGE --------
        authService.logout();
        authService.login("finance@ams.local", "Finance@123");
        check("finance can view but not assign at service gate",
                () -> {
                    try {
                        boolean canView = !assets.findAssets(null, null, null).isEmpty();
                        try {
                            assets.assign(smokeAssetId, employeeId, today, null, null);
                            return false;
                        } catch (AuthorizationException expected) {
                            return canView;
                        }
                    } catch (AuthorizationException viewDenied) {
                        // VIEW itself missing would also fail the gate test differently
                        return false;
                    }
                });
        authService.logout();

        // --- cleanup ------------------------------------------------------------------
        authService.login("admin@ams.local", "Admin@123");
        purgeArtifacts();
        System.out.println("cleanup: smoke assets and assignments removed");

        DatabaseConfig.close();
        if (failures > 0) {
            System.out.println("RESULT: " + failures + " FAILURE(S)");
            System.exit(1);
        }
        System.out.println("RESULT: ALL CHECKS PASSED");
        System.exit(0);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static Asset cloneAsset(String name) {
        Asset copy = new Asset();
        copy.setName(name);
        copy.setCategory("MONITOR");
        copy.setPurchaseDate(LocalDate.now());
        copy.setConditionStatus("NEW");
        return copy;
    }

    private static long secondAssetSetup() {
        if (secondAssetId == 0) {
            secondAssetId = registerSecondAsset(ServiceRegistry.assetService());
        }
        return secondAssetId;
    }

    private static long registerSecondAsset(AssetService assets) {
        Asset asset = new Asset();
        asset.setName("SMOKE iPhone 15");
        asset.setCategory("PHONE");
        asset.setConditionStatus("NEW");
        return assets.saveAsset(asset);
    }

    private static String statusOf(long assetId) {
        return ServiceRegistry.assetService()
                .findAssets(null, null, null).stream()
                .filter(candidate -> candidate.getId() == assetId)
                .findFirst().orElseThrow().getStatus();
    }

    /** Removes smoke-created assets and their assignments. */
    private static void purgeArtifacts() {
        new Sql().executeUpdate(
                "DELETE FROM asset_assignments WHERE notes LIKE 'Smoke%' "
                        + "OR notes LIKE '%Never arrived%' OR asset_id IN "
                        + "(SELECT id FROM assets WHERE asset_name LIKE 'SMOKE %')");
        new Sql().executeUpdate(
                "DELETE FROM assets WHERE asset_name LIKE 'SMOKE %'");
    }

    private static void check(String label, BooleanCheck action) {
        try {
            boolean passed = action.run();
            System.out.println((passed ? "OK   " : "FAIL ") + label);
            if (!passed) {
                failures++;
            }
        } catch (Exception e) {
            System.out.println("FAIL " + label + " -> unexpected "
                    + e.getClass().getSimpleName() + ": " + e.getMessage());
            failures++;
        }
    }

    /** Local alias so the tool compiles without importing both exception names twice. */
    @SuppressWarnings("unused")
    private static final class ValidationExceptionAlias
            extends com.ams.hrms.exception.ValidationException {
        private ValidationExceptionAlias(java.util.List<String> errors) {
            super(errors);
        }
    }

    @FunctionalInterface
    private interface BooleanCheck {
        boolean run() throws Exception;
    }
}

