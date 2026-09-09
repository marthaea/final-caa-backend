package ug.go.caa.recruitment.shared.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiResponse<Void>> validation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        List<ApiError.FieldViolation> violations = exception.getBindingResult().getFieldErrors().stream()
                .map(this::violation)
                .toList();
        return failure(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed", violations, request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiResponse<Void>> constraint(
            ConstraintViolationException exception,
            HttpServletRequest request
    ) {
        List<ApiError.FieldViolation> violations = exception.getConstraintViolations().stream()
                .map(item -> new ApiError.FieldViolation(item.getPropertyPath().toString(), item.getMessage()))
                .toList();
        return failure(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed", violations, request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiResponse<Void>> malformed(HttpMessageNotReadableException exception, HttpServletRequest request) {
        return failure(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "Request body is malformed", List.of(), request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiResponse<Void>> notFound(NoResourceFoundException exception, HttpServletRequest request) {
        return failure(HttpStatus.NOT_FOUND, "NOT_FOUND", "Route not found", List.of(), request);
    }

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiResponse<Void>> api(ApiException exception, HttpServletRequest request) {
        return failure(exception.status(), "API_ERROR", exception.getMessage(), List.of(), request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiResponse<Void>> unexpected(Exception exception, HttpServletRequest request) {
        log.error("Unhandled request failure", exception);
        return failure(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred", List.of(), request);
    }

    private ApiError.FieldViolation violation(FieldError error) {
        return new ApiError.FieldViolation(error.getField(), error.getDefaultMessage());
    }

    private ResponseEntity<ApiResponse<Void>> failure(
            HttpStatus status,
            String code,
            String message,
            List<ApiError.FieldViolation> violations,
            HttpServletRequest request
    ) {
        ApiResponse<Void> body = violations.isEmpty()
                ? ApiResponse.failure(message)
                : ApiResponse.validationFailure("Validation failed", violations);
        return ResponseEntity.status(status)
                .body(body);
    }
}
