package ru.autotestgen.generator;

import java.nio.file.Path;

public class TestConfig {
    private String baseUrl;
    private String login;
    private String password;
    private Path outputDir;
    private String browserType = "chrome";
    private String basePackage = "generated";
    private String siteType = "e3core"; // "e3core", "generic", "custom"
    private String subsystemName = ""; // E3Core: name of subsystem to select after login
    private String testLevel = "basic"; // "smoke", "basic", "full"

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public String getLogin() { return login; }
    public void setLogin(String login) { this.login = login; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public Path getOutputDir() { return outputDir; }
    public void setOutputDir(Path outputDir) { this.outputDir = outputDir; }

    public String getBrowserType() { return browserType; }
    public void setBrowserType(String browserType) { this.browserType = browserType; }

    public String getBasePackage() { return basePackage; }
    public void setBasePackage(String basePackage) { this.basePackage = basePackage; }

    public String getSiteType() { return siteType; }
    public void setSiteType(String siteType) { this.siteType = siteType; }

    public String getSubsystemName() { return subsystemName; }
    public void setSubsystemName(String subsystemName) { this.subsystemName = subsystemName; }

    public String getTestLevel() { return testLevel; }
    public void setTestLevel(String testLevel) { this.testLevel = testLevel; }
}
