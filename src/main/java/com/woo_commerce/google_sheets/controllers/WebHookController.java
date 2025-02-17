package com.woo_commerce.google_sheets.controllers;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.woo_commerce.google_sheets.dto.WebHookRequest;
import com.woo_commerce.google_sheets.services.GoogleSheetService;

@RestController
@RequestMapping("/webhook")
public class WebHookController {
    @Autowired
    private GoogleSheetService service;

    @PostMapping("/listening")
    public void listen(@RequestBody WebHookRequest request, @RequestHeader("userId") String userId) {
        service.updateSheet(request, userId);
    }

    @PostMapping("/listening/many")
    public void listen(@RequestBody List<WebHookRequest> requests, @RequestHeader("userId") String userId) {
        service.updateSheet(requests, userId);
    }
}
