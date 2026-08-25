package com.ams.hrms.tools;

import com.ams.hrms.config.AppConfig;
import com.ams.hrms.config.DatabaseConfig;
import com.ams.hrms.db.DatabaseMigrator;
import com.ams.hrms.db.DbChecker;
import com.ams.hrms.repository.Sql;

/** Development-only: prints row counts + status breakdown for the recruitment tabs. */
public final class RecruitmentDataCheckTool {

    private RecruitmentDataCheckTool() {
    }

    public static void main(String[] args) {
        AppConfig config = AppConfig.get();
        DbChecker.check(config);
        DatabaseConfig.initialize(config);
        DatabaseMigrator.migrate(config);

        System.out.println("== job_vacancies ==");
        new Sql().list("SELECT status, COUNT(*) AS cnt FROM job_vacancies GROUP BY status",
                rs -> {
                    System.out.println("  " + rs.getString("status") + ": " + rs.getLong("cnt"));
                    return rs;
                });

        System.out.println("== candidates ==");
        new Sql().list("SELECT status, COUNT(*) AS cnt FROM candidates GROUP BY status",
                rs -> {
                    System.out.println("  " + rs.getString("status") + ": " + rs.getLong("cnt"));
                    return rs;
                });

        System.out.println("== applications ==");
        new Sql().list("SELECT status, COUNT(*) AS cnt FROM applications GROUP BY status",
                rs -> {
                    System.out.println("  " + rs.getString("status") + ": " + rs.getLong("cnt"));
                    return rs;
                });

        System.out.println("== interviews ==");
        new Sql().list("SELECT result, COUNT(*) AS cnt FROM interviews GROUP BY result",
                rs -> {
                    System.out.println("  " + rs.getString("result") + ": " + rs.getLong("cnt"));
                    return rs;
                });

        System.out.println("== job_offers ==");
        new Sql().list("SELECT status, COUNT(*) AS cnt FROM job_offers GROUP BY status",
                rs -> {
                    System.out.println("  " + rs.getString("status") + ": " + rs.getLong("cnt"));
                    return rs;
                });

        System.out.println("== pipeline consistency ==");
        Long brokenApps = new Sql().queryOne(
                "SELECT COUNT(*) FROM applications a JOIN candidates c ON c.id = a.candidate_id "
                        + "WHERE c.status IN ('REJECTED','WITHDRAWN','HIRED') "
                        + "AND a.status IN ('SUBMITTED','SCREENING','INTERVIEW','OFFER')",
                rs -> rs.getLong(1)).orElse(0L);
        System.out.println("  active applications of terminal candidates: " + brokenApps);
        Long offersWithoutOfferApp = new Sql().queryOne(
                "SELECT COUNT(*) FROM job_offers o JOIN applications a ON a.id = o.application_id "
                        + "WHERE a.status <> 'OFFER' AND o.status IN ('DRAFT','SENT')",
                rs -> rs.getLong(1)).orElse(0L);
        System.out.println("  live offers not on OFFER-stage applications: " + offersWithoutOfferApp);
        Long hiredWithoutEmployee = new Sql().queryOne(
                "SELECT COUNT(*) FROM job_offers o LEFT JOIN employees e ON e.id = o.employee_id "
                        + "WHERE o.status = 'ACCEPTED' AND o.employee_id IS NOT NULL AND e.id IS NULL",
                rs -> rs.getLong(1)).orElse(0L);
        System.out.println("  accepted offers pointing at missing employees: " + hiredWithoutEmployee);

        DatabaseConfig.close();
        System.exit(0);
    }
}
