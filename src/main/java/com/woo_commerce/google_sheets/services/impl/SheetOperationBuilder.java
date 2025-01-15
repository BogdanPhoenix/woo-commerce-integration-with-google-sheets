package com.woo_commerce.google_sheets.services.impl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

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

    public SheetOperationBuilder createSheetIfMissing() {
        try {
            this.needsNewSheet = isSheetMissing();
            if (needsNewSheet) {
                requests.add(createSheetRequest());
            }
        } catch (IOException e) {
            throw new GoogleSheetException("Failed to check sheet existence", e);
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

    private boolean isSheetMissing() throws IOException {
        Spreadsheet spreadsheet = service.spreadsheets()
            .get(spreadsheetId)
            .execute();

        return spreadsheet.getSheets()
            .stream()
            .noneMatch(sheet -> sheet.getProperties().getTitle().equals(sheetName));
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

    public void execute() {
        try {
            if (!requests.isEmpty()) {
                BatchUpdateSpreadsheetRequest batchUpdate = new BatchUpdateSpreadsheetRequest()
                    .setRequests(requests);

                service.spreadsheets()
                    .batchUpdate(spreadsheetId, batchUpdate)
                    .execute();
            }

            if (values != null && !values.isEmpty()) {
                ValueRange body = new ValueRange().setValues(values);

                if (needsNewSheet) {
                    service.spreadsheets().values()
                        .update(spreadsheetId, String.format("%s!A1", sheetName), body)
                        .setValueInputOption("RAW")
                        .execute();
                } else {
                    service.spreadsheets().values()
                        .append(spreadsheetId, sheetName, body)
                        .setValueInputOption("RAW")
                        .setInsertDataOption("INSERT_ROWS")
                        .execute();
                }
            }
        } catch (IOException e) {
            throw new GoogleSheetException("Failed to execute sheet operations", e);
        }
    }
} 