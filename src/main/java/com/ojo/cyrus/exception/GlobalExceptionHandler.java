package com.ojo.cyrus.exception;

import com.ojo.cyrus.enums.ResponseCode;
import com.ojo.cyrus.models.responses.CyrusApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;
import java.util.UUID;

/**
 * Translates exceptions into {@link CyrusApiResponse} envelopes carrying an {@link ErrorDetails}
 * payload. Two tiers:
 *   - Client (4xx) errors: the thrown message is already safe/actionable, so it is returned as-is
 *     and logged at debug.
 *   - Server (5xx) / upstream errors: the real cause is logged at error with a short traceId, and
 *     the client receives a generic friendly message plus that traceId for support correlation —
 *     internal/provider detail is never leaked.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ---- Client errors (4xx): safe to echo the message back ------------------------------------

    @ExceptionHandler(AlreadyExistsException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public CyrusApiResponse<ErrorDetails> handleAlreadyExists(AlreadyExistsException ex) {
        return clientError(ResponseCode.DUPLICATE_MERCHANT, ex.getMessage());
    }

    @ExceptionHandler(EntityNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public CyrusApiResponse<ErrorDetails> handleNotFound(EntityNotFoundException ex) {
        return clientError(ResponseCode.RESOURCE_NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(InvalidTokenException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public CyrusApiResponse<ErrorDetails> handleInvalidToken(InvalidTokenException ex) {
        return clientError(ResponseCode.INVALID_TOKEN, ex.getMessage());
    }

    @ExceptionHandler(BadCredentialsException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public CyrusApiResponse<ErrorDetails> handleBadCredentials() {
        // Deliberately generic — never reveal whether the email or the password was wrong.
        return clientError(ResponseCode.UNAUTHORIZED, "Invalid email or password");
    }

    @ExceptionHandler(DisabledException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public CyrusApiResponse<ErrorDetails> handleDisabled() {
        return clientError(ResponseCode.ACCOUNT_NOT_VERIFIED,
                "Account is not yet verified. Please check your email for the verification link.");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public CyrusApiResponse<ErrorDetails> handleValidation(MethodArgumentNotValidException ex) {
        List<ErrorDetails.FieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ErrorDetails.FieldError(fe.getField(), fe.getDefaultMessage()))
                .toList();
        String message = "One or more fields are invalid";
        log.debug("Validation failure: {}", fieldErrors);
        return CyrusApiResponse.failure(ResponseCode.INVALID_INPUT, message,
                ErrorDetails.validation(ResponseCode.INVALID_INPUT.name(), message, fieldErrors));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public CyrusApiResponse<ErrorDetails> handleUnreadableBody(HttpMessageNotReadableException ex) {
        log.debug("Unreadable request body: {}", ex.getMessage());
        return clientError(ResponseCode.INVALID_REQUEST, "Malformed or missing request body");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public CyrusApiResponse<ErrorDetails> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String message = "Invalid value for parameter '" + ex.getName() + "'";
        log.debug("Type mismatch: {}", ex.getMessage());
        return clientError(ResponseCode.INVALID_REQUEST, message);
    }

    // ---- Server / upstream errors (5xx): log the real cause, return a generic message ----------

    @ExceptionHandler(NombaIntegrationException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public CyrusApiResponse<ErrorDetails> handleNombaIntegration(NombaIntegrationException ex) {
        return serverError(ResponseCode.NOMBA_INTEGRATION_ERROR,
                "We couldn't complete your request with the payment provider. Please try again shortly.", ex);
    }

    @ExceptionHandler(EmailSendingException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public CyrusApiResponse<ErrorDetails> handleEmailSending(EmailSendingException ex) {
        return serverError(ResponseCode.EMAIL_DELIVERY_ERROR,
                "We couldn't send the email at this time. Please try again shortly.", ex);
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public CyrusApiResponse<ErrorDetails> handleGeneral(Exception ex) {
        return serverError(ResponseCode.INTERNAL_ERROR, "An unexpected error occurred", ex);
    }

    // ---- Helpers -------------------------------------------------------------------------------

    private CyrusApiResponse<ErrorDetails> clientError(ResponseCode code, String message) {
        log.debug("Client error {}: {}", code.name(), message);
        return CyrusApiResponse.failure(code, message, ErrorDetails.of(code.name(), message));
    }

    private CyrusApiResponse<ErrorDetails> serverError(ResponseCode code, String friendlyMessage, Throwable ex) {
        String traceId = UUID.randomUUID().toString().substring(0, 8);
        log.error("[{}] {}: {}", traceId, code.name(), ex.getMessage(), ex);
        return CyrusApiResponse.failure(code, friendlyMessage, ErrorDetails.of(code.name(), friendlyMessage, traceId));
    }
}
