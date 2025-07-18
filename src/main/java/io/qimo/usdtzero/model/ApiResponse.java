package io.qimo.usdtzero.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {
    private int code;
    private String message;
    private T data;

    public ApiResponse(ErrorCode errorCode, T data) {
        this.code = errorCode.getCode();
        this.message = errorCode.getMessage();
        this.data = data;
    }

    public ApiResponse(ErrorCode errorCode, String customMsg, T data) {
        this.code = errorCode.getCode();
        this.message = customMsg != null ? customMsg : errorCode.getMessage();
        this.data = data;
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(ErrorCode.SUCCESS, data);
    }

    public static <T> ApiResponse<T> fail(ErrorCode errorCode, String msg) {
        return new ApiResponse<>(errorCode, msg, null);
    }
} 