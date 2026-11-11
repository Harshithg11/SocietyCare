package com.societycare.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorResponse(
        LocalDateTime timestamp,
        int status,
        String error,
        String message,
        String path,
        Map<String, String> validationErrors
) {
    public static ApiErrorResponse of(HttpStatus status, String message, String path,
                                      Map<String, String> validationErrors) {
        return new ApiErrorResponse(LocalDateTime.now().withNano(0), status.value(),
                status.getReasonPhrase(), message, path, validationErrors);
    }
}
