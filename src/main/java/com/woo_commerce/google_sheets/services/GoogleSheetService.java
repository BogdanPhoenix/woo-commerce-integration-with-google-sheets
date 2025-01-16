package com.woo_commerce.google_sheets.services;

import java.util.List;

import com.woo_commerce.google_sheets.dto.WebHookRequest;
import com.woo_commerce.google_sheets.exception.GoogleSheetException;

public interface GoogleSheetService {
    void updateSheet(WebHookRequest request, String userId) throws GoogleSheetException;
    void updateSheet(List<WebHookRequest> requests, String userId) throws GoogleSheetException;
}
