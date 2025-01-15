package com.woo_commerce.google_sheets.services;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;

import com.woo_commerce.google_sheets.dto.WebHookRequest;

public interface GoogleSheetService {
    void updateSheet(WebHookRequest request, String userId) throws IOException, GeneralSecurityException;
    void updateSheet(List<WebHookRequest> requests, String userId) throws IOException, GeneralSecurityException;
}
