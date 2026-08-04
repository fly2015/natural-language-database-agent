package com.nlda.generation;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "agent.llm")
public class SqlGenerationProperties {

    private String provider = "deterministic";
    private String apiKey = "";
    private String model = "gpt-4.1-mini";
    private String baseUrl = "";
    private Duration timeout = Duration.ofSeconds(30);
    private int maxRetries = 1;
    private int repairRetries = 1;

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public int getRepairRetries() {
        return repairRetries;
    }

    public void setRepairRetries(int repairRetries) {
        this.repairRetries = repairRetries;
    }
}
