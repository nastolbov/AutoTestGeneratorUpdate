package ru.autotestgen.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class TestRunResult {
    private int totalTests;
    private int passed;
    private int failed;
    private int skipped;
    private LocalDateTime runTimestamp;
    private long durationMs;
    private String xmlFileName;
    private String baseUrl;
    private List<TestCaseResult> results = new ArrayList<>();
    private String mavenOutput = "";

    public int getTotalTests() { return totalTests; }
    public void setTotalTests(int totalTests) { this.totalTests = totalTests; }

    public int getPassed() { return passed; }
    public void setPassed(int passed) { this.passed = passed; }

    public int getFailed() { return failed; }
    public void setFailed(int failed) { this.failed = failed; }

    public int getSkipped() { return skipped; }
    public void setSkipped(int skipped) { this.skipped = skipped; }

    public LocalDateTime getRunTimestamp() { return runTimestamp; }
    public void setRunTimestamp(LocalDateTime runTimestamp) { this.runTimestamp = runTimestamp; }

    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }

    public String getXmlFileName() { return xmlFileName; }
    public void setXmlFileName(String xmlFileName) { this.xmlFileName = xmlFileName; }

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public List<TestCaseResult> getResults() { return results; }
    public void setResults(List<TestCaseResult> results) { this.results = results; }

    public String getMavenOutput() { return mavenOutput; }
    public void setMavenOutput(String mavenOutput) { this.mavenOutput = mavenOutput; }
}
