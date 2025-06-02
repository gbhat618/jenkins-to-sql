package com.gbhat618.jenkinstosql;

import java.io.File;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JenkinsMonitorApp {
    private static final Logger logger = LoggerFactory.getLogger(JenkinsMonitorApp.class);

    public static void main(String[] args) {
        try {
            String jenkinsHome = System.getenv().getOrDefault("JENKINS_HOME", "/var/lib/jenkins");
            String dbUrl = System.getenv("DB_URL");
            String intervalStr = System.getenv().getOrDefault("POLL_INTERVAL_SECONDS", "5");

            if (dbUrl == null || dbUrl.isEmpty()) {
                throw new IllegalArgumentException("DB_URL env must be set");
            }

            int intervalSeconds = Integer.parseInt(intervalStr);
            logger.info("Starting Jenkins Build Monitor with poll interval {} seconds", intervalSeconds);

            var jobsDir = new File(jenkinsHome, "jobs/");
            var dbWriter = new DatabaseWriter(dbUrl);
            var scanner = new JenkinsScanner(jobsDir, dbWriter);

            try (var scheduler = Executors.newScheduledThreadPool(1)) {
                scheduler.scheduleAtFixedRate(scanner::scan, 0, intervalSeconds, TimeUnit.SECONDS);
                scheduler.awaitTermination(1, TimeUnit.DAYS);
            }
        } catch (Exception e) {
            logger.error("Fatal error during app startup", e);
            System.exit(1);
        }
    }
}