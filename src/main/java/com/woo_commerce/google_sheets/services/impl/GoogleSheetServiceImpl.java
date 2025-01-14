package com.woo_commerce.google_sheets.services.impl;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
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
import com.woo_commerce.google_sheets.dto.WebHookRequest;
import com.woo_commerce.google_sheets.services.GoogleSheetService;

@Service
public class GoogleSheetServiceImpl implements GoogleSheetService {
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final List<String> SCOPES = Collections.singletonList(SheetsScopes.SPREADSHEETS);
    private static final String TOKENS_DIRECTORY_PATH = "tokens";
    private static final int PORT = 8888;
    private static List<Object> HEADERS = Arrays.asList("ID", "ITEM", "AMOUNT", "PRICE", "COST");
    private static String RANGE = "Sheet1!A1:E1";

    @Value("${google.project.name}")
    private String applicationName;

    @Value("${google.credentials.path}")
    private String credentialsFilePath;

    @Value("${google.project.table.suppliers.id}")
    private String suppliersId;

    @Value("${google.project.table.customers.id}")
    private String customerId;

    @Override
    public void updateSheet(WebHookRequest request, String userId) throws IOException, GeneralSecurityException {
        CustomerSheet customerSheet = new CustomerSheet();

        customerSheet.updateSheet(userId, request);
    }


    private Sheets getSheetsService() throws IOException, GeneralSecurityException {
        final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
        
        return new Sheets.Builder(HTTP_TRANSPORT, JSON_FACTORY, getCredentials(HTTP_TRANSPORT))
                .setApplicationName(applicationName)
                .build();
    }

    /**
     * Creates an authorized Credential object.
     *
     * @param HTTP_TRANSPORT The network HTTP Transport.
     * @return An authorized Credential object.
     * @throws IOException If the credentials.json file cannot be found.
     */
    private Credential getCredentials(final NetHttpTransport HTTP_TRANSPORT) throws IOException {
        InputStream in = GoogleSheetServiceImpl.class.getResourceAsStream(credentialsFilePath);
        
        if (in == null) {
            throw new FileNotFoundException("Resource not found: " + credentialsFilePath);
        
        }
        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));

        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
            HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES)
            .setDataStoreFactory(new FileDataStoreFactory(new java.io.File(TOKENS_DIRECTORY_PATH)))
            .setAccessType("offline")
            .build();

        LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(PORT).build();
        return new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");
    }

    private class CustomerSheet {
        private void updateSheet(String sheetName, WebHookRequest request) throws IOException, GeneralSecurityException {
            Sheets service = getSheetsService();
            Sheet sheet = getSheet(service, customerId, sheetName);

            if(sheet.isEmpty()) {
                addTitleToSheet(service);
            } 

            addOrderToEnd(service, sheetName, request);
        }
    
        private Sheet getSheet(Sheets service, String spreadsheetId, String sheetName) throws IOException {
            Spreadsheet spreadsheet = service.spreadsheets().get(spreadsheetId).execute();
            List<Sheet> sheets = spreadsheet.getSheets();
            
            return sheets.stream()
                .filter(sheet -> sheet.getProperties().getTitle().equals(sheetName))
                .findFirst()
                .orElse(createSheet(service, spreadsheetId, sheetName));
        }
    
        private Sheet createSheet(Sheets service, String spreadsheetId, String sheetName) throws IOException {
            AddSheetRequest addSheetRequest = new AddSheetRequest()
                .setProperties(new SheetProperties().setTitle(sheetName));
            Request request = new Request().setAddSheet(addSheetRequest);
            BatchUpdateSpreadsheetRequest batchUpdateRequest = new BatchUpdateSpreadsheetRequest()
                    .setRequests(Collections.singletonList(request));
    
            BatchUpdateSpreadsheetResponse response = service.spreadsheets()
                .batchUpdate(spreadsheetId, batchUpdateRequest)
                .execute();
    
            SheetProperties properties = response.getReplies()
                .get(0)
                .getAddSheet()
                .getProperties();
        
            return new Sheet()
                .setProperties(properties);
        }

        private void addTitleToSheet(Sheets service) throws IOException{
            ValueRange body = new ValueRange().setValues(Arrays.asList(Arrays.asList(HEADERS)));

            service.spreadsheets().values()
                .update(customerId, RANGE, body)
                .setValueInputOption("RAW")
                .execute();
        }

        private void addOrderToEnd(Sheets service, String sheetName, WebHookRequest request) throws IOException {
            ValueRange body = new ValueRange().setValues(Arrays.asList(request.getSheetRow()));
            service.spreadsheets().values()
                .append(customerId, sheetName, body)
                .setValueInputOption("RAW")
                .setInsertDataOption("INSERT_ROWS")
                .execute();
        }
    }
    
}
