package com.ojo.cyrus.exception;

import lombok.Getter;

/**
 * A single-field validation failure detected in a service rather than by bean validation — e.g.
 * "required if present" semantics on a partial-update DTO, where {@code @NotBlank} can't be used
 * because {@code null} (leave unchanged) must stay valid. Mapped to the same
 * {@code ErrorDetails.fieldErrors} shape as {@link org.springframework.web.bind.MethodArgumentNotValidException}
 * so callers get a consistent error shape regardless of which layer caught the violation.
 */
@Getter
public class FieldValidationException extends RuntimeException {
    private final String field;

    public FieldValidationException(String field, String message) {
        super(message);
        this.field = field;
    }
}
