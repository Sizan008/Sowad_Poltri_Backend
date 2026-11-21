package com.sowad.nourish.common.exception;

import com.sowad.nourish.common.response.ApiError;

import jakarta.validation.ConstraintViolationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> validation(MethodArgumentNotValidException error) {
        Map<String, String> fields = new LinkedHashMap<>();
        error.getBindingResult()
                .getFieldErrors()
                .forEach(field -> fields.putIfAbsent(field.getField(), field.getDefaultMessage()));
        return response(400, "VALIDATION_ERROR", "Please check the supplied values.", fields);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> constraints(ConstraintViolationException error) {
        Map<String,String> fields = new LinkedHashMap<>();
        error.getConstraintViolations().stream()
            .sorted(java.util.Comparator.comparing(v -> v.getPropertyPath().toString()))
            .forEach(v -> fields.putIfAbsent(v.getPropertyPath().toString(), v.getMessage()));
        return response(400, "VALIDATION_ERROR", "Please check the supplied values.", fields);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> invalidInput(Exception error) {
        return response(400, "INVALID_REQUEST", "Invalid request data.", Map.of());
    }

    @ExceptionHandler(com.sowad.nourish.catalog.CatalogFailure.class)
    public ResponseEntity<ApiError> catalog(com.sowad.nourish.catalog.CatalogFailure error) {
        return response(error.status(), "CATALOG_" + error.status(), error.getMessage(), Map.of());
    }

    @ExceptionHandler(com.sowad.nourish.common.masterdata.MasterDataException.class)
    public ResponseEntity<ApiError> masterData(com.sowad.nourish.common.masterdata.MasterDataException error) {
        return response(error.status(), "MASTER_DATA_" + error.status(), error.getMessage(), Map.of());
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiError> business(BusinessException error) {
        return response(400, "BUSINESS_VALIDATION", error.getMessage(), Map.of());
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiError> database(DataAccessException error) {
        LOGGER.error("Database operation failed: {}", error.getClass().getSimpleName());
        return response(
                503,
                "DATABASE_UNAVAILABLE",
                "Database is unavailable or its schema is not ready.",
                Map.of());
    }

    @ExceptionHandler(org.springframework.security.core.AuthenticationException.class)
    public ResponseEntity<ApiError> authentication(Exception error) {
        return response(401, "UNAUTHORIZED", "Invalid credentials or session.", Map.of());
    }

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<ApiError> forbidden(Exception error) {
        return response(403, "FORBIDDEN", "Permission denied.", Map.of());
    }

    @ExceptionHandler({
        org.springframework.dao.DataIntegrityViolationException.class,
        org.springframework.orm.ObjectOptimisticLockingFailureException.class
    })
    public ResponseEntity<ApiError> conflict(Exception error) {
        return response(
                409, "CONFLICT", "Data changed or already exists. Refresh and retry.", Map.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> unexpected(Exception error) {
        if (error instanceof ErrorResponse webError) {
            int status = webError.getStatusCode().value();
            return response(
                    status, "HTTP_" + status, "The request could not be processed.", Map.of());
        }
        LOGGER.error("Request failed: {}", error.getClass().getSimpleName());
        return response(500, "INTERNAL_ERROR", "An unexpected error occurred.", Map.of());
    }

    private ResponseEntity<ApiError> response(
            int status, String code, String message, Map<String, String> fields) {
        return ResponseEntity.status(status)
                .body(new ApiError(code, message, fields, MDC.get("requestId"), Instant.now()));
    }
}
