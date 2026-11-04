package com.gateway.api.exception;

import com.gateway.api.dto.ProblemDetailDTO;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GatewayExceptionHandler {

    private static final Logger log =
            LoggerFactory.getLogger(GatewayExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetailDTO> handleValidationException(
            MethodArgumentNotValidException exception,
            HttpServletRequest request) {

        String detail = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));

        ProblemDetailDTO problem = new ProblemDetailDTO(
                "https://api.gateway.io/errors/validation-failed",
                "Bad Request",
                HttpStatus.BAD_REQUEST.value(),
                detail,
                request.getRequestURI(),
                Instant.now()
        );

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(problem);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetailDTO> handleMalformedJson(
            HttpMessageNotReadableException exception,
            HttpServletRequest request) {

        ProblemDetailDTO problem = new ProblemDetailDTO(
                "https://api.gateway.io/errors/malformed-json",
                "Malformed JSON",
                HttpStatus.BAD_REQUEST.value(),
                "The request body is invalid or cannot be parsed.",
                request.getRequestURI(),
                Instant.now()
        );

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(problem);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetailDTO> handleUnexpectedException(
            Exception exception,
            HttpServletRequest request) {

        log.error(
                "Unexpected exception while processing {} {}",
                request.getMethod(),
                request.getRequestURI(),
                exception
        );

        ProblemDetailDTO problem = new ProblemDetailDTO(
                "https://api.gateway.io/errors/internal-server-error",
                "Internal Server Error",
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "An unexpected error occurred.",
                request.getRequestURI(),
                Instant.now()
        );

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(problem);
    }
}