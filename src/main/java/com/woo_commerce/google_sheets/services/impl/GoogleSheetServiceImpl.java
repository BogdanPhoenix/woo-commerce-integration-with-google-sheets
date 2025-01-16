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
import com.woo_commerce.google_sheets.exception.GoogleSheetException.ErrorCode;
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
    public GoogleSheetServiceImpl(GoogleSheetConfig config) throws GoogleSheetException {
        this.config = config;
        this.sheetsCache = createSheetsCache();
        this.service = getSheetsService();
    }

    @Override
    public void updateSheet(WebHookRequest request, String userId) throws GoogleSheetException {
        List<List<Object>> buffer = Collections.singletonList(request.getSheetRow());
        executeForCustomer(buffer, userId);
    }

    @Override
    public void updateSheet(List<WebHookRequest> requests, String userId) throws GoogleSheetException {
        List<List<Object>> buffer = requests.stream()
            .map(r -> r.getSheetRow())
            .toList();
        
        executeForCustomer(buffer, userId);
    }

    private void executeForCustomer(List<List<Object>> requests, String userId) throws GoogleSheetException {
        try {
            verifySpreadsheetAccess(config.getCustomersId());

            new SheetOperationBuilder(this.service, config.getCustomersId())
                .forSheet(userId)
                .createSheetIfMissing()
                .withHeaders(HEADERS)
                .withValues(requests)
                .execute();

        } catch (GoogleSheetException e) {
            log.error("Failed to update sheet for user: {}", userId, e);
            throw new GoogleSheetException(ErrorCode.OPERATION_FAILED, "Failed to update sheet for user: " + userId, e);
        }
    }

    private Sheets createSheetsService() throws GoogleSheetException {
        try {
            final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
            return new Sheets.Builder(HTTP_TRANSPORT, JSON_FACTORY, getCredentials(HTTP_TRANSPORT))
                    .setApplicationName(config.getApplicationName())
                    .build();
        } catch (IOException e) {
            throw new GoogleSheetException(ErrorCode.AUTHENTICATION_FAILED, "Failed to create Google Sheets service: " + e.getMessage(), e);
        } catch (GeneralSecurityException e) {
            throw new GoogleSheetException(ErrorCode.AUTHENTICATION_FAILED, "Security error while creating Google Sheets service: " + e.getMessage(), e);
        }
    }

    private Sheets getSheetsService() throws GoogleSheetException {
        try {
            return sheetsCache.get("sheets");
        } catch (ExecutionException e) {
            log.error("Error getting sheets service from cache", e);
            throw new GoogleSheetException(ErrorCode.SERVICE_UNAVAILABLE, "Failed to get Google Sheets service: " + e.getMessage(), e);
        }
    }

    private Credential getCredentials(final NetHttpTransport HTTP_TRANSPORT) throws GoogleSheetException {
        try (InputStream in = new ClassPathResource(config.getCredentialsFilePath()).getInputStream()) {
            if (in == null) {
                throw new GoogleSheetException(ErrorCode.AUTHENTICATION_FAILED, "Credentials file not found at path: " + config.getCredentialsFilePath());
            }

            GoogleCredentials credentials = GoogleCredentials.fromStream(in)
                    .createScoped(SCOPES);
            credentials.refreshIfExpired();
            
            return new Credential(BearerToken.authorizationHeaderAccessMethod())
                .setAccessToken(credentials.getAccessToken().getTokenValue());
        } catch (IOException e) {
            throw new GoogleSheetException(ErrorCode.AUTHENTICATION_FAILED, "Failed to load Google Sheets credentials: " + e.getMessage(), e);
        }
    }

    private void verifySpreadsheetAccess(String spreadsheetId) throws GoogleSheetException {
        try {
            this.service
                .spreadsheets()
                .get(spreadsheetId)
                .execute();

        } catch (GoogleJsonResponseException e) {
            switch (e.getStatusCode()) {
                case 403:
                    throw new GoogleSheetException(ErrorCode.PERMISSION_DENIED, "Service account doesn't have access to spreadsheet: " + spreadsheetId, e);
                case 404:
                    throw new GoogleSheetException(ErrorCode.SHEET_NOT_FOUND, "Spreadsheet not found: " + spreadsheetId, e);
                default:
                    throw new GoogleSheetException(ErrorCode.SERVICE_UNAVAILABLE, "Google Sheets API error: " + e.getMessage(), e);
            }
        } catch (IOException e) {
            throw new GoogleSheetException(ErrorCode.SERVICE_UNAVAILABLE, "Failed to verify spreadsheet access", e);
        }
    }

    private LoadingCache<String, Sheets> createSheetsCache() {
        return CacheBuilder.newBuilder()
                .expireAfterWrite(1, TimeUnit.HOURS)
                .build(new CacheLoader<String, Sheets>() {
                    @Override
                    public Sheets load(@Nonnull String key) throws GoogleSheetException {
                        return createSheetsService();
                    }
                });
    }
}