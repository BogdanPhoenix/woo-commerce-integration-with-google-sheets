package com.woo_commerce.google_sheets.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

@Data
@Configuration
@ConfigurationProperties(prefix = "google.project")
public class GoogleSheetConfig {
    private String name;
    private String credentialsPath;
    private Table table = new Table();

    @Data
    public static class Table {
        private String suppliersId;
        private String customersId;
    }

    public String getApplicationName() {
        return name;
    }

    public String getCredentialsFilePath() {
        return credentialsPath;
    }

    public String getCustomersId() {
        return table.getCustomersId();
    }
} 