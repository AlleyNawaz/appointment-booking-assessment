package com.clinic.booking.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
public class CustomErrorController implements ErrorController {

    @RequestMapping("/error")
    public ResponseEntity<ErrorResponse> handleError(HttpServletRequest request) {
        Integer statusCode = (Integer) request.getAttribute("jakarta.servlet.error.status_code");
        HttpStatus status = statusCode != null ? HttpStatus.valueOf(statusCode) : HttpStatus.INTERNAL_SERVER_ERROR;
        
        String errorCode = status == HttpStatus.NOT_FOUND ? "NOT_FOUND" : status.name();
        String message = status == HttpStatus.NOT_FOUND ? "Resource not found." : "An unexpected error occurred.";
        
        String path = (String) request.getAttribute("jakarta.servlet.error.request_uri");
        if (path == null) {
            path = request.getRequestURI();
        }

        ErrorResponse body = new ErrorResponse(
                Instant.now(),
                status.value(),
                errorCode,
                message,
                path,
                List.of()
        );
        return ResponseEntity.status(status).body(body);
    }
}
