package com.quidoitquoi.api.exceptions;

import java.time.Instant;
import java.util.Locale;
import java.util.stream.Collectors;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.security.core.AuthenticationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import com.quidoitquoi.api.config.ApiLocale;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final MessageSource messageSource;

    @Autowired
    public GlobalExceptionHandler(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    public GlobalExceptionHandler() {
        ResourceBundleMessageSource fallbackMessageSource = new ResourceBundleMessageSource();
        fallbackMessageSource.setBasename("messages");
        fallbackMessageSource.setDefaultEncoding("UTF-8");
        fallbackMessageSource.setFallbackToSystemLocale(false);
        this.messageSource = fallbackMessageSource;
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ApiError> handleUserNotFound(
            UserNotFoundException exception,
            HttpServletRequest request) {
        return buildResponse(HttpStatus.NOT_FOUND, "api.error.user-not-found",
                new Object[] { exception.getId() }, request);
    }

    @ExceptionHandler(GroupNotFoundException.class)
    public ResponseEntity<ApiError> handleGroupNotFound(
            GroupNotFoundException exception,
            HttpServletRequest request) {
        return buildResponse(HttpStatus.NOT_FOUND, "api.error.group-not-found",
                new Object[] { exception.getId() }, request);
    }

    @ExceptionHandler(MemberNotFoundException.class)
    public ResponseEntity<ApiError> handleMemberNotFound(
            MemberNotFoundException exception,
            HttpServletRequest request) {
        return buildResponse(HttpStatus.NOT_FOUND, "api.error.member-not-found",
                new Object[] { exception.getId() }, request);
    }

    @ExceptionHandler(ExpenseNotFoundException.class)
    public ResponseEntity<ApiError> handleExpenseNotFound(
            ExpenseNotFoundException exception,
            HttpServletRequest request) {
        return buildResponse(HttpStatus.NOT_FOUND, "api.error.expense-not-found",
                new Object[] { exception.getId() }, request);
    }

    @ExceptionHandler(ForbiddenOperationException.class)
    public ResponseEntity<ApiError> handleForbiddenOperation(
            ForbiddenOperationException exception,
            HttpServletRequest request) {
        return buildResponse(HttpStatus.FORBIDDEN, "api.error.forbidden-operation", request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception,
            HttpServletRequest request) {
        Locale locale = resolveLocale(request);
        String message = exception.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .filter(java.util.Objects::nonNull)
                .map(validationMessage -> localizeValidationMessage(validationMessage, locale))
                .distinct()
                .collect(Collectors.joining("; "));
        if (message.isBlank()) {
            message = exception.getBindingResult().getGlobalErrors().stream()
                    .map(error -> error.getDefaultMessage() == null
                            ? getMessageWithDefault(
                                    "api.error.invalid-request",
                                    "The request is invalid",
                                    locale)
                            : localizeValidationMessage(error.getDefaultMessage(), locale))
                    .collect(Collectors.joining("; "));
        }
        if (message.isBlank()) {
            message = getMessageWithDefault(
                    "api.error.invalid-request",
                    "The request is invalid",
                    locale);
        }
        return buildResponse(HttpStatus.BAD_REQUEST, message, request);
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<ApiError> handleBadRequest(Exception exception, HttpServletRequest request) {
        return buildResponse(HttpStatus.BAD_REQUEST, "api.error.invalid-request", request);
    }

    @ExceptionHandler(InvalidGroupImageException.class)
    public ResponseEntity<ApiError> handleInvalidGroupImage(
            InvalidGroupImageException exception,
            HttpServletRequest request) {
        return buildResponse(HttpStatus.BAD_REQUEST, exception.getMessageKey(), request);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> handleMaxUploadSizeExceeded(
            MaxUploadSizeExceededException exception,
            HttpServletRequest request) {
        return buildResponse(HttpStatus.PAYLOAD_TOO_LARGE, "api.error.image-too-large", request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(
            ConstraintViolationException exception,
            HttpServletRequest request) {
        Locale locale = resolveLocale(request);
        String message = exception.getConstraintViolations().stream()
                .map(violation -> localizeValidationMessage(violation.getMessage(), locale))
                .distinct()
                .collect(Collectors.joining("; "));
        return buildResponse(HttpStatus.BAD_REQUEST, message, request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrityViolation(
            DataIntegrityViolationException exception,
            HttpServletRequest request) {
        return buildResponse(HttpStatus.CONFLICT, "api.error.data-conflict", request);
    }

    @ExceptionHandler(EmailAlreadyUsedException.class)
    public ResponseEntity<ApiError> handleEmailAlreadyUsed(
            EmailAlreadyUsedException exception,
            HttpServletRequest request) {
        return buildResponse(HttpStatus.CONFLICT, "api.error.email-already-used",
                new Object[] { exception.getEmail() }, request);
    }

    @ExceptionHandler(InvalidAuthRequestException.class)
    public ResponseEntity<ApiError> handleInvalidAuthRequest(
            InvalidAuthRequestException exception,
            HttpServletRequest request) {
        return buildResponse(HttpStatus.BAD_REQUEST, exception.getMessageKey(), request);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> handleAuthenticationFailure(
            AuthenticationException exception,
            HttpServletRequest request) {
        return buildResponse(HttpStatus.UNAUTHORIZED, "api.error.invalid-credentials", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpectedException(
            Exception exception,
            HttpServletRequest request) {
        logger.error("Unhandled exception while processing {}", request.getRequestURI(), exception);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "api.error.unexpected", request);
    }

    private ResponseEntity<ApiError> buildResponse(
            HttpStatus status,
            String messageKey,
            HttpServletRequest request) {
        return buildResponse(status, messageKey, null, request);
    }

    private ResponseEntity<ApiError> buildResponse(
            HttpStatus status,
            String messageKey,
            Object[] arguments,
            HttpServletRequest request) {
        Locale locale = resolveLocale(request);
        ApiError error = new ApiError(
                Instant.now(),
                status.value(),
                getMessageWithDefault("api.error." + status.name().toLowerCase(Locale.ROOT).replace('_', '-'),
                        status.getReasonPhrase(), locale),
                getMessage(messageKey, arguments, locale),
                request.getRequestURI());
        return ResponseEntity.status(status).body(error);
    }

    private String localizeValidationMessage(String message, Locale locale) {
        if (message.startsWith("{") && message.endsWith("}")) {
            return getMessageWithDefault(message.substring(1, message.length() - 1), message, locale);
        }
        return message;
    }

    private String getMessage(String code, Object[] arguments, Locale locale) {
        return messageSource.getMessage(code, arguments, code, locale);
    }

    private String getMessageWithDefault(String code, String defaultMessage, Locale locale) {
        return messageSource.getMessage(code, null, defaultMessage, locale);
    }

    private Locale resolveLocale(HttpServletRequest request) {
        return ApiLocale.fromRequest(request);
    }
}
