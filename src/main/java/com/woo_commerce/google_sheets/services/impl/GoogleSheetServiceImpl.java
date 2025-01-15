package com.woo_commerce.google_sheets.services.impl;

import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import com.google.api.client.auth.oauth2.BearerToken;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.auth.oauth2.GoogleCredentials;
import com.woo_commerce.google_sheets.dto.WebHookRequest;
import com.woo_commerce.google_sheets.exception.GoogleSheetException;
import com.woo_commerce.google_sheets.services.GoogleSheetService;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.woo_commerce.google_sheets.config.GoogleSheetConfig;

import lombok.extern.slf4j.Slf4j;
import javax.annotation.Nonnull;

@Service
@Slf4j
public class GoogleSheetServiceImpl implements GoogleSheetService {
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final List<String> SCOPES = Collections.singletonList(SheetsScopes.SPREADSHEETS);
    private static final List<Object> HEADERS = Arrays.asList("ID", "ITEM", "AMOUNT", "PRICE", "COST");
    
    private final GoogleSheetConfig config;
    private final LoadingCache<String, Sheets> sheetsCache;
    private final Sheets service;

    @Autowired
    public GoogleSheetServiceImpl(GoogleSheetConfig config) throws IOException {
        this.config = config;
        this.sheetsCache = createSheetsCache();
        this.service = getSheetsService();
    }

    @Override
    public void updateSheet(WebHookRequest request, String userId) {
        try {
            verifySpreadsheetAccess(config.getCustomersId());

            new SheetOperationBuilder(this.service, config.getCustomersId())
                .forSheet(userId)
                .createSheetIfMissing()
                .withHeaders(HEADERS)
                .withValues(Collections.singletonList(request.getSheetRow()))
                .execute();

        } catch (Exception e) {
            log.error("Failed to update sheet for user: {}", userId, e);
            throw new GoogleSheetException("Failed to update sheet", e);
        }
    }

    private Sheets createSheetsService() throws IOException, GeneralSecurityException {
        final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
        return new Sheets.Builder(HTTP_TRANSPORT, JSON_FACTORY, getCredentials(HTTP_TRANSPORT))
                .setApplicationName(config.getApplicationName())
                .build();
    }

    private Sheets getSheetsService() throws IOException {
        try {
            return sheetsCache.get("sheets");
        } catch (ExecutionException e) {
            log.error("Error getting sheets service from cache", e);
            throw new IOException("Failed to get sheets service", e);
        }
    }

    private Credential getCredentials(final NetHttpTransport HTTP_TRANSPORT) throws IOException {
        InputStream in = new ClassPathResource(config.getCredentialsFilePath()).getInputStream();
        try {
            GoogleCredentials credentials = GoogleCredentials.fromStream(in)
                    .createScoped(SCOPES);
            credentials.refreshIfExpired();
            
            return new Credential(BearerToken.authorizationHeaderAccessMethod())
                .setAccessToken(credentials.getAccessToken().getTokenValue());
        } finally {
            in.close();
        }
    }

    private void verifySpreadsheetAccess(String spreadsheetId) throws IOException {
        try {
            this.service
                .spreadsheets()
                .get(spreadsheetId)
                .execute();

        } catch (GoogleJsonResponseException e) {
            if (e.getStatusCode() == 403) {
                throw new SecurityException("Service account doesn't have access to spreadsheet: " + spreadsheetId);
            }
            throw e;
        }
    }

    private LoadingCache<String, Sheets> createSheetsCache() {
        return CacheBuilder.newBuilder()
                .expireAfterWrite(1, TimeUnit.HOURS)
                .build(new CacheLoader<String, Sheets>() {
                    @Override
                    public Sheets load(@Nonnull String key) throws Exception {
                        return createSheetsService();
                    }
                });
    }
}