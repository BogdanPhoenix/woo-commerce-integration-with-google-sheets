package com.woo_commerce.google_sheets.services.impl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.AddSheetRequest;
import com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetRequest;
import com.google.api.services.sheets.v4.model.GridProperties;
import com.google.api.services.sheets.v4.model.Request;
import com.google.api.services.sheets.v4.model.SheetProperties;
import com.google.api.services.sheets.v4.model.Spreadsheet;
import com.google.api.services.sheets.v4.model.ValueRange;

import lombok.extern.slf4j.Slf4j;
import com.woo_commerce.google_sheets.exception.GoogleSheetException;
import com.woo_commerce.google_sheets.exception.GoogleSheetException.ErrorCode;

@Slf4j
public class SheetOperationBuilder {
    private static final int rowCount = 500;
    private static final int columnCount = 5;
    
    private final List<Request> requests = new ArrayList<>();
    private final String spreadsheetId;
    private final Sheets service;

    private String sheetName;
    private List<List<Object>> values;
    private boolean needsNewSheet = false;

    public SheetOperationBuilder(Sheets service, String spreadsheetId) {
        this.service = service;
        this.spreadsheetId = spreadsheetId;
        this.values = new ArrayList<>();
    }

    public SheetOperationBuilder forSheet(String sheetName) {
        this.sheetName = sheetName;
        return this;
    }

    public SheetOperationBuilder createSheetIfMissing() throws GoogleSheetException {
        this.needsNewSheet = isSheetMissing();
        
        if (needsNewSheet) {
            requests.add(createSheetRequest());
        }
        
        return this;
    }

    public SheetOperationBuilder withHeaders(List<Object> headers) {
        if (needsNewSheet) {
            this.values.add(headers);
        }

        return this;
    }

    public SheetOperationBuilder withValues(List<List<Object>> values) {
        this.values.addAll(values);
        return this;
    }

    private boolean isSheetMissing() throws GoogleSheetException {
        try {
            Spreadsheet spreadsheet = service.spreadsheets()
                .get(spreadsheetId)
                .execute();

            return spreadsheet.getSheets()
                .stream()
                .noneMatch(sheet -> sheet.getProperties().getTitle().equals(sheetName));
        } catch (IOException e) {
            throw new GoogleSheetException(ErrorCode.SHEET_NOT_FOUND, "Worksheet could not be found by the specified ID: " + spreadsheetId, e);
        }
    }

    private Request createSheetRequest() {
        SheetProperties properties = new SheetProperties()
            .setTitle(sheetName)
            .setGridProperties(new GridProperties()
                .setRowCount(rowCount)
                .setColumnCount(columnCount)
                .setFrozenRowCount(1));

        return new Request()
            .setAddSheet(new AddSheetRequest().setProperties(properties));
    }

    public void execute() throws GoogleSheetException {
        try {
            executeBatchUpdates()
                .thenCompose(v -> executeValueUpdates())
                .join();
        } catch (Exception e) {
            throw new GoogleSheetException(ErrorCode.OPERATION_FAILED, "Failed to execute sheet operations", e);
        }
    }

    private CompletableFuture<Void> executeBatchUpdates() {
        if (requests.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.runAsync(() -> {
            try {
                BatchUpdateSpreadsheetRequest batchUpdate = new BatchUpdateSpreadsheetRequest()
                    .setRequests(requests);

                service.spreadsheets()
                    .batchUpdate(spreadsheetId, batchUpdate)
                    .execute();
            } catch (IOException e) {
                throw new GoogleSheetException(ErrorCode.OPERATION_FAILED, "Failed to execute batch update operations", e);
            }
        });
    }

    private CompletableFuture<Void> executeValueUpdates() {
        if (values == null || values.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.runAsync(() -> {
            try {
                ValueRange body = new ValueRange().setValues(values);
                
                if (needsNewSheet) {
                    updateNewSheet(body);
                } else {
                    appendToExistingSheet(body);
                }
            } catch (IOException e) {
                throw new GoogleSheetException(ErrorCode.OPERATION_FAILED, "Failed to update sheet values", e);
            }
        });
    }

    private void updateNewSheet(ValueRange body) throws IOException {
        service.spreadsheets().values()
            .update(spreadsheetId, String.format("%s!A1", sheetName), body)
            .setValueInputOption("RAW")
            .execute();
    }

    private void appendToExistingSheet(ValueRange body) throws IOException {
        service.spreadsheets().values()
            .append(spreadsheetId, sheetName, body)
            .setValueInputOption("RAW")
            .setInsertDataOption("INSERT_ROWS")
            .execute();
    }
} 