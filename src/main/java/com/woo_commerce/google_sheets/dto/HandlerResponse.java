package com.woo_commerce.google_sheets.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class HandlerResponse {
    private String status;
    private String errorMessage;
}
