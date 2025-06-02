package com.gbhat618.jenkinstosql;

import java.sql.Timestamp;

public class BuildRecord {
    public String jobName;
    public int buildNumber;
    public Timestamp startTime;
    public Timestamp endTime;
    public String status;

    public BuildRecord(String jobName, int buildNumber, Timestamp startTime, Timestamp endTime, String status) {
        this.jobName = jobName;
        this.buildNumber = buildNumber;
        this.startTime = startTime;
        this.endTime = endTime;
        this.status = status;
    }
}