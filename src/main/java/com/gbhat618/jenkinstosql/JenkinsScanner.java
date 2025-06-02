package com.gbhat618.jenkinstosql;

import java.io.File;
import java.sql.Timestamp;
import java.util.List;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class JenkinsScanner {
    private static final Logger logger = LoggerFactory.getLogger(JenkinsScanner.class);
    private final File jobsDir;
    private final DatabaseWriter dbWriter;

    public JenkinsScanner(File jobsDir, DatabaseWriter dbWriter) {
        this.jobsDir = jobsDir;
        this.dbWriter = dbWriter;
    }

    public void scan() {
        logger.info("Starting scan cycle");
        scanJobsRecursive(jobsDir, "");
        logger.info("Scan cycle complete");
    }

    private void scanJobsRecursive(File currentDir, String pathPrefix) {
        if (!currentDir.exists()) return;

        File[] children = currentDir.listFiles();
        if (children == null) return;

        for (File child : children) {
            if (!child.isDirectory()) continue;

            File configXml = new File(child, "config.xml");
            File innerJobs = new File(child, "jobs");
            File buildsDir = new File(child, "builds");

            if (configXml.exists() && buildsDir.exists()) {
                String relativeJobPath = pathPrefix.isEmpty() ? child.getName() : pathPrefix + "/" + child.getName();
                processJob(relativeJobPath, buildsDir);
            }

            if (innerJobs.exists() && innerJobs.isDirectory()) {
                String newPrefix = pathPrefix.isEmpty() ? child.getName() : pathPrefix + "/" + child.getName();
                scanJobsRecursive(innerJobs, newPrefix);
            }
        }
    }

    private void processJob(String jobName, File buildsDir) {

        List<Integer> nonTerminalBuilds = dbWriter.getNonTerminalBuilds(jobName);
        for (int buildNumber : nonTerminalBuilds) {
            File buildXml = new File(new File(buildsDir, Integer.toString(buildNumber)), "build.xml");
            if (!buildXml.exists()) continue;

            try {
                BuildRecord rec = parseBuildXml(jobName, buildNumber, buildXml);
                if (isTerminalStatus(rec.status)) {
                    dbWriter.upsertBuild(rec);
                    logger.info("Updated terminal build='{}'#{} status={}", jobName, rec.buildNumber, rec.status);
                } else {
                    logger.debug("Build {} for job {} still not terminal", buildNumber, jobName);
                }
            } catch (Exception e) {
                logger.error("Error reprocessing non-terminal build {} for job {}", buildNumber, jobName, e);
            }
        }

        int lastProcessed = dbWriter.getLastBuildNumber(jobName);
        logger.info("Checking new builds for job '{}'. Last processed build: {}", jobName, lastProcessed);
        int nextBuild = lastProcessed + 1;

        while (true) {
            File buildDir = new File(buildsDir, Integer.toString(nextBuild));
            File buildXml = new File(buildDir, "build.xml");
            if (!buildXml.exists())
                break;

            try {
                BuildRecord rec = parseBuildXml(jobName, nextBuild, buildXml);
                dbWriter.saveBuild(rec);
                logger.info("Recorded job='{}' build={} status={}", jobName, rec.buildNumber, rec.status);
                nextBuild++;
            } catch (Exception e) {
                logger.error("Error processing build {} for job {}", nextBuild, jobName, e);
                break;  // Don't skip next builds if error encountered
            }
        }
    }

    private static final Set<String> VALID_JENKINS_BUILD_ROOT_TAGS = Set.of("build", "flow-build", "matrix-build", "run");

    private BuildRecord parseBuildXml(String jobName, int buildNum, File xmlFile) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(xmlFile);
            doc.getDocumentElement().normalize();

            Element root = doc.getDocumentElement();
            String rootTag = root.getTagName();

            if (!VALID_JENKINS_BUILD_ROOT_TAGS.contains(rootTag)) {
                logger.warn("Skipping build #{} for job '{}': unknown root tag <{}>", buildNum, jobName, rootTag);
                return null;
            }

            String startTimeStr = getDirectChild(root, "startTime");
            if (startTimeStr == null || startTimeStr.isBlank()) {
                logger.warn("Missing <startTime> in build #{} for '{}', skipping", buildNum, jobName);
                return null;
            }

            long startMillis = Long.parseLong(startTimeStr);

            String durationStr = getDirectChild(root, "duration");
            long durationMillis = durationStr != null && !durationStr.isBlank()
                                  ? Long.parseLong(durationStr)
                                  : 0;

            String result = getDirectChild(root, "result");  // Only top-level result tag

            Timestamp start = new Timestamp(startMillis);
            Timestamp end = new Timestamp(startMillis + durationMillis);

            return new BuildRecord(jobName, buildNum, start, end, result);

        } catch (Exception e) {
            logger.error("Error parsing build.xml for job '{}' build #{}: {}", jobName, buildNum, e.toString());
            logger.debug("Full stack trace", e);
            return null;
        }
    }

    private String getDirectChild(Element root, String desiredTagName) {
        NodeList children = root.getChildNodes();

        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && desiredTagName.equals(child.getNodeName())) {
                return child.getTextContent();
            }
        }
        return null;
    }

    private boolean isTerminalStatus(String status) {
        return status != null && List.of("SUCCESS", "FAILURE", "ABORTED").contains(status);
    }
}