package ru.autotestgen.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TestCaseResult {
    private String className;
    private String methodName;
    private boolean passed;
    private boolean skipped;
    private String failureMessage;
    private long durationMs;
    // Дополнительные данные по тесту для HTML/CSV-отчёта о прогоне.
    private String stdOut = "";
    private Map<String, String> searchParams = new LinkedHashMap<>();
    private List<String> screenshots = new ArrayList<>();
    private List<StepTiming> steps = new ArrayList<>();

    public String getClassName() { return className; }
    public void setClassName(String className) { this.className = className; }

    public String getMethodName() { return methodName; }
    public void setMethodName(String methodName) { this.methodName = methodName; }

    public boolean isPassed() { return passed; }
    public void setPassed(boolean passed) { this.passed = passed; }

    public boolean isSkipped() { return skipped; }
    public void setSkipped(boolean skipped) { this.skipped = skipped; }

    public String getFailureMessage() { return failureMessage; }
    public void setFailureMessage(String failureMessage) { this.failureMessage = failureMessage; }

    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }

    public String getStdOut() { return stdOut; }
    public void setStdOut(String stdOut) { this.stdOut = stdOut == null ? "" : stdOut; }

    public Map<String, String> getSearchParams() { return searchParams; }
    public void setSearchParams(Map<String, String> p) { this.searchParams = p == null ? new LinkedHashMap<>() : p; }

    public List<String> getScreenshots() { return screenshots; }
    public void addScreenshot(String path) { this.screenshots.add(path); }

    public List<StepTiming> getSteps() { return steps; }
    public void addStep(String name, long ms) { this.steps.add(new StepTiming(name, ms)); }

    public static final class StepTiming {
        public final String name;
        public final long ms;
        public StepTiming(String name, long ms) { this.name = name; this.ms = ms; }
    }
}
