package com.ojo.cyrus.exception;

import com.ojo.cyrus.enums.ResponseCode;
import com.ojo.cyrus.models.responses.CyrusApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Translates exceptions into {@link CyrusApiResponse} envelopes carrying an {@link ErrorDetails}
 * payload.
 *
 * <p>Every handler logs the actual throwable (with stack), so a service can simply {@code throw}
 * and rely on this advice for the diagnostic log entry — no need to log before throwing. Two tiers:
 * <ul>
 *   <li>Client (4xx) errors: logged at warn; the thrown message is safe/actionable and returned as-is.</li>
 *   <li>Server (5xx) / upstream errors: logged at error with a short traceId; the client receives a
 *       generic friendly message plus that traceId for support correlation — internal/provider
 *       detail is never leaked.</li>
 * </ul>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ---- Client errors (4xx): safe to echo the message back ------------------------------------

    @ExceptionHandler(AlreadyExistsException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public CyrusApiResponse<ErrorDetails> handleAlreadyExists(AlreadyExistsException ex) {
        return clientError(ResponseCode.DUPLICATE_MERCHANT, ex.getMessage(), ex);
    }

    @ExceptionHandler(EntityNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public CyrusApiResponse<ErrorDetails> handleNotFound(EntityNotFoundException ex) {
        return clientError(ResponseCode.RESOURCE_NOT_FOUND, ex.getMessage(), ex);
    }

    @ExceptionHandler(InvalidTokenException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public CyrusApiResponse<ErrorDetails> handleInvalidToken(InvalidTokenException ex) {
        return clientError(ResponseCode.INVALID_TOKEN, ex.getMessage(), ex);
    }

    @ExceptionHandler(EmailNotVerifiedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public CyrusApiResponse<ErrorDetails> handleEmailNotVerified(EmailNotVerifiedException ex) {
        return clientError(ResponseCode.ACCOUNT_NOT_VERIFIED, ex.getMessage(), ex);
    }

    @ExceptionHandler(BadCredentialsException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public CyrusApiResponse<ErrorDetails> handleBadCredentials(BadCredentialsException ex) {
        // Deliberately generic to the client — never reveal whether the email or the password was wrong.
        return clientError(ResponseCode.UNAUTHORIZED, "Invalid email or password", ex);
    }

    @ExceptionHandler(DisabledException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public CyrusApiResponse<ErrorDetails> handleDisabled(DisabledException ex) {
        return clientError(ResponseCode.ACCOUNT_NOT_VERIFIED,
                "Account is not yet verified. Please check your email for the verification link.", ex);
    }

    @ExceptionHandler(WebhookSignatureException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public CyrusApiResponse<ErrorDetails> handleWebhookSignature(WebhookSignatureException ex) {
        return clientError(ResponseCode.UNAUTHORIZED, "Invalid webhook signature", ex);
    }

    @ExceptionHandler(NombaVerificationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public CyrusApiResponse<ErrorDetails> handleNombaVerification(NombaVerificationException ex) {
        return clientError(ResponseCode.INVALID_REQUEST, ex.getMessage(), ex);
    }

    @ExceptionHandler(InvalidWebhookUrlException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public CyrusApiResponse<ErrorDetails> handleInvalidWebhookUrl(InvalidWebhookUrlException ex) {
        return clientError(ResponseCode.INVALID_REQUEST, ex.getMessage(), ex);
    }

    @ExceptionHandler(InvalidPaymentEventStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public CyrusApiResponse<ErrorDetails> handleInvalidPaymentEventState(InvalidPaymentEventStateException ex) {
        return clientError(ResponseCode.INVALID_REQUEST, ex.getMessage(), ex);
    }

    @ExceptionHandler(InvalidApiKeyStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public CyrusApiResponse<ErrorDetails> handleInvalidApiKeyState(InvalidApiKeyStateException ex) {
        return clientError(ResponseCode.INVALID_REQUEST, ex.getMessage(), ex);
    }

    @ExceptionHandler(InvalidCustomerStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public CyrusApiResponse<ErrorDetails> handleInvalidCustomerState(InvalidCustomerStateException ex) {
        return clientError(ResponseCode.INVALID_REQUEST, ex.getMessage(), ex);
    }

    @ExceptionHandler(InsufficientFundsException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public CyrusApiResponse<ErrorDetails> handleInsufficientFunds(InsufficientFundsException ex) {
        return clientError(ResponseCode.INVALID_REQUEST, ex.getMessage(), ex);
    }

    @ExceptionHandler(AccountVerificationException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_CONTENT)
    public CyrusApiResponse<ErrorDetails> handleAccountVerification(AccountVerificationException ex) {
        return clientError(ResponseCode.INVALID_REQUEST, ex.getMessage(), ex);
    }

    @ExceptionHandler(ForbiddenException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public CyrusApiResponse<ErrorDetails> handleForbidden(ForbiddenException ex) {
        return clientError(ResponseCode.UNAUTHORIZED, ex.getMessage(), ex);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public CyrusApiResponse<ErrorDetails> handleDataIntegrity(DataIntegrityViolationException ex) {
        return serverError(ResponseCode.INVALID_REQUEST,
                "Data integrity violation: " + ex.getMostSpecificCause().getMessage(), ex);
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public CyrusApiResponse<ErrorDetails> handleIllegalState(IllegalStateException ex) {
        return serverError(ResponseCode.INVALID_REQUEST, ex.getMessage(), ex);
    }

    @ExceptionHandler(NoSuchElementException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public CyrusApiResponse<ErrorDetails> handleNoSuchElement(NoSuchElementException ex) {
        return clientError(ResponseCode.RESOURCE_NOT_FOUND, ex.getMessage(), ex);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public CyrusApiResponse<ErrorDetails> handleValidation(MethodArgumentNotValidException ex) {
        List<ErrorDetails.FieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ErrorDetails.FieldError(fe.getField(), fe.getDefaultMessage()))
                .toList();
        String message = "One or more fields are invalid";
        log.warn("{} -> {}: {} | fields={}", ex.getClass().getSimpleName(), ResponseCode.INVALID_INPUT.name(),
                message, fieldErrors, ex);
        return CyrusApiResponse.failure(ResponseCode.INVALID_INPUT, message,
                ErrorDetails.ofFieldErrors(fieldErrors));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public CyrusApiResponse<ErrorDetails> handleUnreadableBody(HttpMessageNotReadableException ex) {
        return clientError(ResponseCode.INVALID_REQUEST, "Malformed or missing request body", ex);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public CyrusApiResponse<ErrorDetails> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return clientError(ResponseCode.INVALID_REQUEST, "Invalid value for parameter '" + ex.getName() + "'", ex);
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

    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public CyrusApiResponse<ErrorDetails> handleNoResource(NoResourceFoundException ex) {
        return CyrusApiResponse.failure(ResponseCode.RESOURCE_NOT_FOUND, "Resource not found");
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public CyrusApiResponse<ErrorDetails> handleGeneral(Exception ex) {
        return serverError(ResponseCode.INTERNAL_ERROR, "An unexpected error occurred", ex);
    }

    // ---- Helpers -------------------------------------------------------------------------------

    private CyrusApiResponse<ErrorDetails> clientError(ResponseCode code, String message, Throwable ex) {
        log.warn("{} -> {}: {}", ex.getClass().getSimpleName(), code.name(), message, ex);
        // Plain client errors are fully described by the envelope — no extra ErrorDetails payload.
        return CyrusApiResponse.failure(code, message);
    }

    private CyrusApiResponse<ErrorDetails> serverError(ResponseCode code, String friendlyMessage, Throwable ex) {
        String traceId = UUID.randomUUID().toString().substring(0, 8);
        log.error("[{}] {} -> {}: {}", traceId, ex.getClass().getSimpleName(), code.name(), ex.getMessage(), ex);
        return CyrusApiResponse.failure(code, friendlyMessage, ErrorDetails.ofTrace(traceId));
    }
}
