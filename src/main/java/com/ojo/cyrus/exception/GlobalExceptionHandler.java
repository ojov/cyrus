package com.ojo.cyrus.exception;

import com.ojo.cyrus.models.responses.CyrusApiResponse;
import com.ojo.cyrus.enums.ResponseCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AlreadyExistsException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public CyrusApiResponse<Void> handleAlreadyExists(AlreadyExistsException ex) {
        return CyrusApiResponse.failure(ResponseCode.DUPLICATE_MERCHANT, ex.getMessage());
    }

    @ExceptionHandler(InvalidTokenException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public CyrusApiResponse<Void> handleInvalidToken(InvalidTokenException ex) {
        return CyrusApiResponse.failure(ResponseCode.INVALID_TOKEN, ex.getMessage());
    }

    @ExceptionHandler(BadCredentialsException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public CyrusApiResponse<Void> handleBadCredentials(BadCredentialsException ex) {
        return CyrusApiResponse.failure(ResponseCode.UNAUTHORIZED, "Invalid email or password");
    }

    @ExceptionHandler(DisabledException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public CyrusApiResponse<Void> handleDisabled(DisabledException ex) {
        return CyrusApiResponse.failure(ResponseCode.ACCOUNT_NOT_VERIFIED,
                "Account is not yet verified. Please check your email for the verification link.");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public CyrusApiResponse<Void> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        return CyrusApiResponse.failure(ResponseCode.INVALID_INPUT, message);
    }

    @ExceptionHandler(NombaIntegrationException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public CyrusApiResponse<Void> handleNombaIntegration(NombaIntegrationException ex) {
        log.error("Nomba integration error", ex);
        return CyrusApiResponse.failure(ResponseCode.NOMBA_INTEGRATION_ERROR, ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public CyrusApiResponse<Void> handleGeneral(Exception ex) {
        log.error("Unexpected error", ex);
        return CyrusApiResponse.failure(ResponseCode.INTERNAL_ERROR, "An unexpected error occurred");
    }
}
