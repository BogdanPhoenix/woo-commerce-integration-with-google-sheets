package com.woo_commerce.google_sheets.dto;

import java.math.BigDecimal;
import java.util.List;

import lombok.Data;

@Data
public class WebHookRequest {
    private String id;
    private String productName;
    private int quantity;
    private BigDecimal price;
    private BigDecimal cost;

    public List<Object> getSheetRow() {
        return List.of(id, productName, quantity, price, cost);
    }
}
