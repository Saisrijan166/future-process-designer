package com.assesswise.processdesigner.controller;

import com.assesswise.processdesigner.exception.AiNotConfiguredException;
import com.assesswise.processdesigner.exception.AiProviderException;
import com.assesswise.processdesigner.exception.AnalysisFailedException;
import com.assesswise.processdesigner.exception.AnalysisInProgressException;
import com.assesswise.processdesigner.exception.RateLimitExceededException;
import com.assesswise.processdesigner.exception.ResourceNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Turns every failure into an RFC 7807 problem document with a message a human can act on.
 *
 * <p>Failure modes here are first-class product behaviour, not an afterthought: a demo where the
 * free-tier quota runs out should say "quota exceeded, try again in a minute", not show a spinner
 * forever or a stack trace.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleNotFound(ResourceNotFoundException e, HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, "Not found", e.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException e, HttpServletRequest request) {
        Map<String, String> fieldErrors = new TreeMap<>();
        for (FieldError error : e.getBindingResult().getFieldErrors()) {
            fieldErrors.put(error.getField(), error.getDefaultMessage());
        }
        e.getBindingResult().getGlobalErrors()
                .forEach(error -> fieldErrors.put(error.getObjectName(), error.getDefaultMessage()));

        ProblemDetail detail = problem(
                HttpStatus.BAD_REQUEST, "Invalid request", "The request body failed validation.", request);
        detail.setProperty("errors", fieldErrors);
        return detail;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleUnreadable(HttpMessageNotReadableException e, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "Malformed request",
                "The request body could not be read as JSON.", request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException e, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid parameter",
                "'%s' is not a valid value for %s.".formatted(e.getValue(), e.getName()), request);
    }

    @ExceptionHandler(AnalysisInProgressException.class)
    public ProblemDetail handleInProgress(AnalysisInProgressException e, HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, "Analysis already running", e.getMessage(), request);
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ProblemDetail> handleRateLimit(RateLimitExceededException e, HttpServletRequest request) {
        ProblemDetail detail =
                problem(HttpStatus.TOO_MANY_REQUESTS, "Rate limit exceeded", e.getMessage(), request);
        detail.setProperty("retryAfterSeconds", e.getRetryAfterSeconds());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(e.getRetryAfterSeconds()))
                .body(detail);
    }

    @ExceptionHandler(AiNotConfiguredException.class)
    public ProblemDetail handleNotConfigured(AiNotConfiguredException e, HttpServletRequest request) {
        log.error("Analysis requested but the AI provider is not configured");
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "AI provider not configured", e.getMessage(), request);
    }

    @ExceptionHandler(AiProviderException.class)
    public ProblemDetail handleProvider(AiProviderException e, HttpServletRequest request) {
        log.error("AI provider call failed: {}", e.getMessage());
        ProblemDetail detail = problem(HttpStatus.BAD_GATEWAY, "AI provider error", e.getMessage(), request);
        detail.setProperty("retryable", e.isRetryable());
        return detail;
    }

    @ExceptionHandler(AnalysisFailedException.class)
    public ProblemDetail handleAnalysisFailed(AnalysisFailedException e, HttpServletRequest request) {
        log.warn("Analysis produced unusable output: {} ({})", e.getMessage(), e.getDetail());
        ProblemDetail detail =
                problem(HttpStatus.UNPROCESSABLE_ENTITY, "Analysis failed", e.getMessage(), request);
        detail.setProperty("reason", e.getDetail());
        return detail;
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrity(DataIntegrityViolationException e, HttpServletRequest request) {
        log.error("Database constraint violated", e);
        return problem(HttpStatus.CONFLICT, "Conflict",
                "The change conflicts with data already stored.", request);
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception e, HttpServletRequest request) {
        log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), e);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error",
                "Something went wrong on the server. Check the service logs for details.", request);
    }

    private ProblemDetail problem(HttpStatus status, String title, String detail, HttpServletRequest request) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
        problemDetail.setTitle(title);
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("timestamp", Instant.now().toString());
        properties.put("path", request.getRequestURI());
        properties.forEach(problemDetail::setProperty);
        return problemDetail;
    }
}
