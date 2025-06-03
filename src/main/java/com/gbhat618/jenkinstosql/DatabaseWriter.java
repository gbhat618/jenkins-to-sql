package com.gbhat618.jenkinstosql;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DatabaseWriter {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseWriter.class);
    private final String url;

    public DatabaseWriter(String dbUrl) {
        this.url = dbUrl;
        try {
            createTableIfNotExists();
        } catch (SQLException e) {
            logger.error("error creating table", e);
        }
    }

    private void createTableIfNotExists() throws SQLException {
        try (Connection conn = DriverManager.getConnection(url);
            Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS builds (" +
                "id SERIAL PRIMARY KEY, " +
                "job_name TEXT NOT NULL, " +
                "build_number INT NOT NULL, " +
                "start_time TIMESTAMP, " +
                "end_time TIMESTAMP, " +
                "status TEXT, " +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "UNIQUE(job_name, build_number))"
            );
        }
    }

    public int getLastBuildNumber(String jobName) {
        try (Connection conn = DriverManager.getConnection(url);
            PreparedStatement ps = conn.prepareStatement(
                "SELECT MAX(build_number) FROM builds WHERE job_name = ?")) {
            ps.setString(1, jobName);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            logger.error("Failed to retrieve last build number for {}", jobName, e);
        }
        return 0;
    }

    public List<Integer> getNonTerminalBuilds(String jobName) {
        List<Integer> builds = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(url);
            /* Various possible states are, SUCCESS, UNSTABLE, FAILURE, NOT_BUILT, ABORTED */
            PreparedStatement ps = conn.prepareStatement(
                "SELECT build_number FROM builds WHERE job_name = ? AND status NOT IN ('SUCCESS', 'FAILURE', "
                + "'ABORTED', 'UNSTABLE', 'NOT_BUILT') OR status is NULL")) {
            ps.setString(1, jobName);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                builds.add(rs.getInt("build_number"));
            }
        } catch (SQLException e) {
            logger.error("Error fetching non-terminal builds for {}", jobName, e);
        }
        return builds;
    }

    public void saveBuild(BuildRecord build) {
        try (Connection conn = DriverManager.getConnection(url);
            PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO builds(job_name, build_number, start_time, end_time, status) " +
                "VALUES (?, ?, ?, ?, ?) ON CONFLICT (job_name, build_number) DO NOTHING")) {
            ps.setString(1, build.jobName);
            ps.setInt(2, build.buildNumber);
            ps.setTimestamp(3, build.startTime);
            ps.setTimestamp(4, build.endTime);
            ps.setString(5, build.status);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.error("Failed to insert build record for {} #{}", build.jobName, build.buildNumber, e);
        }
    }

    public void upsertBuild(BuildRecord build) throws SQLException {
        try (Connection conn = DriverManager.getConnection(url)) {
            conn.setAutoCommit(false);
            try (PreparedStatement delete = conn.prepareStatement(
                "DELETE FROM builds WHERE job_name = ? AND build_number = ?");
                PreparedStatement insert = conn.prepareStatement(
                    "INSERT INTO builds (job_name, build_number, start_time, end_time, status) " +
                    "VALUES (?, ?, ?, ?, ?)")) {

                delete.setString(1, build.jobName);
                delete.setInt(2, build.buildNumber);
                delete.executeUpdate();

                insert.setString(1, build.jobName);
                insert.setInt(2, build.buildNumber);
                insert.setTimestamp(3, build.startTime);
                insert.setTimestamp(4, build.endTime);
                insert.setString(5, build.status);
                insert.executeUpdate();

                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        }
    }
}