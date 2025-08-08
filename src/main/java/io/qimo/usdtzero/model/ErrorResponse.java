package io.qimo.usdtzero.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ErrorResponse {
    private int code;
    private String message;

    public ErrorResponse(ErrorCode errorCode, String customMsg) {
        this.code = errorCode.getCode();
        this.message = customMsg != null ? customMsg : errorCode.getMessage();
    }

    public ErrorResponse(ErrorCode errorCode) {
        this.code = errorCode.getCode();
        this.message = errorCode.getMessage();
    }
} 