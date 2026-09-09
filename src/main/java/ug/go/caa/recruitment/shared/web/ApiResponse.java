package ug.go.caa.recruitment.shared.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        boolean success,
        T data,
        Integer total,
        String error,
        List<ApiError.FieldViolation> errors
) {
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null, null, null);
    }

    public static <T> ApiResponse<T> list(T data, int total) {
        return new ApiResponse<>(true, data, total, null, null);
    }

    public static <T> ApiResponse<T> failure(String error) {
        return new ApiResponse<>(false, null, null, error, null);
    }

    public static <T> ApiResponse<T> validationFailure(
            String error,
            List<ApiError.FieldViolation> errors
    ) {
        return new ApiResponse<>(false, null, null, error, errors);
    }
}
