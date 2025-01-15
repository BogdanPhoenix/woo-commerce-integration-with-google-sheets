package com.woo_commerce.google_sheets.services.impl;

import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
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
import com.google.api.services.sheets.v4.model.AddSheetRequest;
import com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetRequest;
import com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetResponse;
import com.google.api.services.sheets.v4.model.Request;
import com.google.api.services.sheets.v4.model.Sheet;
import com.google.api.services.sheets.v4.model.SheetProperties;
import com.google.api.services.sheets.v4.model.Spreadsheet;
import com.google.api.services.sheets.v4.model.ValueRange;
import com.google.auth.oauth2.GoogleCredentials;
import com.woo_commerce.google_sheets.dto.WebHookRequest;
import com.woo_commerce.google_sheets.services.GoogleSheetService;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

import lombok.extern.slf4j.Slf4j;
import javax.annotation.Nonnull;

@Service
@Slf4j
public class GoogleSheetServiceImpl implements GoogleSheetService {
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final List<String> SCOPES = Collections.singletonList(SheetsScopes.SPREADSHEETS);
    private static List<Object> HEADERS = Arrays.asList("ID", "ITEM", "AMOUNT", "PRICE", "COST");
    private static String RANGE = "Sheet1!A1:E1";
    
    private final LoadingCache<String, Sheets> sheetsCache;

    @Value("${google.project.name}")
    private String applicationName;

    @Value("${google.credentials.path}")
    private String credentialsFilePath;

    @Value("${google.project.table.suppliers.id}")
    private String suppliersId;

    @Value("${google.project.table.customers.id}")
    private String customerId;

    public GoogleSheetServiceImpl() {
        this.sheetsCache = CacheBuilder.newBuilder()
                .expireAfterWrite(1, TimeUnit.HOURS)
                .build(new CacheLoader<String, Sheets>() {
                    @Override
                    public Sheets load(@Nonnull String key) throws Exception {
                        return createSheetsService();
                    }
                });
    }

    @Override
    public void updateSheet(WebHookRequest request, String userId) throws IOException, GeneralSecurityException {
        CustomerSheet customerSheet = new CustomerSheet();
        customerSheet.updateSheet(userId, request);
    }

    private Sheets createSheetsService() throws IOException, GeneralSecurityException {
        final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
        return new Sheets.Builder(HTTP_TRANSPORT, JSON_FACTORY, getCredentials(HTTP_TRANSPORT))
                .setApplicationName(applicationName)
                .build();
    }

    private Sheets getSheetsService() throws IOException, GeneralSecurityException {
        try {
            return sheetsCache.get("sheets");
        } catch (ExecutionException e) {
            log.error("Error getting sheets service from cache", e);
            throw new IOException("Failed to get sheets service", e);
        }
    }

    private Credential getCredentials(final NetHttpTransport HTTP_TRANSPORT) throws IOException {
        InputStream in = new ClassPathResource(credentialsFilePath).getInputStream();
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
            getSheetsService().spreadsheets().get(spreadsheetId).execute();
        } catch (GoogleJsonResponseException e) {
            if (e.getStatusCode() == 403) {
                throw new SecurityException("Service account doesn't have access to spreadsheet: " + spreadsheetId);
            }
            throw e;
        } catch (GeneralSecurityException e) {
            throw new IOException("Failed to verify spreadsheet access", e);
        }
    }

    private class CustomerSheet {
        private void updateSheet(String sheetName, WebHookRequest request) throws IOException, GeneralSecurityException {
            Sheets service = getSheetsService();
            verifySpreadsheetAccess(customerId);

            Sheet sheet = getSheet(service, customerId, sheetName);

            if(sheet.isEmpty()) {
                addTitleToSheet(service);
            } 

            addOrderToEnd(service, sheetName, request);
        }
    
        private Sheet getSheet(Sheets service, String spreadsheetId, String sheetName) throws IOException {
            try {
                Spreadsheet spreadsheet = service.spreadsheets().get(spreadsheetId).execute();
                List<Sheet> sheets = spreadsheet.getSheets();
                
                return sheets.stream()
                    .filter(sheet -> sheet.getProperties().getTitle().equals(sheetName))
                    .findFirst()
                    .orElseGet(() -> {
                        try {
                            return createSheet(service, spreadsheetId, sheetName);
                        } catch (IOException e) {
                            log.error("Error creating new sheet", e);
                            throw new RuntimeException("Failed to create new sheet", e);
                        }
                    });
            } catch (GoogleJsonResponseException e) {
                log.error("Error getting sheet", e);
                throw e;
            }
        }
    
        private Sheet createSheet(Sheets service, String spreadsheetId, String sheetName) throws IOException {
            AddSheetRequest addSheetRequest = new AddSheetRequest()
                .setProperties(new SheetProperties().setTitle(sheetName));
            Request request = new Request().setAddSheet(addSheetRequest);
            BatchUpdateSpreadsheetRequest batchUpdateRequest = new BatchUpdateSpreadsheetRequest()
                    .setRequests(Collections.singletonList(request));
    
            try {
                BatchUpdateSpreadsheetResponse response = service.spreadsheets()
                    .batchUpdate(spreadsheetId, batchUpdateRequest)
                    .execute();
        
                SheetProperties properties = response.getReplies()
                    .get(0)
                    .getAddSheet()
                    .getProperties();
            
                return new Sheet()
                    .setProperties(properties);
            } catch (GoogleJsonResponseException e) {
                log.error("Error creating sheet: {}", sheetName, e);
                throw e;
            }
        }

        private void addTitleToSheet(Sheets service) throws IOException {
            ValueRange body = new ValueRange().setValues(Arrays.asList(Arrays.asList(HEADERS)));

            try {
                service.spreadsheets().values()
                    .update(customerId, RANGE, body)
                    .setValueInputOption("RAW")
                    .execute();
            } catch (GoogleJsonResponseException e) {
                log.error("Error adding title to sheet", e);
                throw e;
            }
        }

        private void addOrderToEnd(Sheets service, String sheetName, WebHookRequest request) throws IOException {
            ValueRange body = new ValueRange().setValues(Arrays.asList(request.getSheetRow()));
            try {
                service.spreadsheets().values()
                    .append(customerId, sheetName, body)
                    .setValueInputOption("RAW")
                    .setInsertDataOption("INSERT_ROWS")
                    .execute();
            } catch (GoogleJsonResponseException e) {
                log.error("Error adding order to sheet", e);
                throw e;
            }
        }
    }
}