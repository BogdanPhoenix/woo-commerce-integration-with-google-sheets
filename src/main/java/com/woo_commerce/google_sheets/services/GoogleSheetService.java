package com.woo_commerce.google_sheets.services;

import java.io.IOException;
import java.security.GeneralSecurityException;

import com.woo_commerce.google_sheets.dto.WebHookRequest;

public interface GoogleSheetService {
    void updateSheet(WebHookRequest request, String userId) throws IOException, GeneralSecurityException;
}
