package com.example.wallet.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(String errorCode, String message, List<String> details) {

    public ErrorResponse(String errorCode, String message) {
        this(errorCode, message, null);
    }
}
