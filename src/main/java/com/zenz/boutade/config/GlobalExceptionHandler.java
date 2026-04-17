package com.zenz.boutade.config;

import com.zenz.boutade.route.exception.BadRequestException;
import com.zenz.boutade.route.exception.ConflictException;
import com.zenz.boutade.route.exception.NotFoundException;
import com.zenz.boutade.route.response.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFoundException(final NotFoundException e) {
        final ErrorResponse response = new ErrorResponse();
        response.setMessage(e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ErrorResponse> handleBadRequestException(final BadRequestException e) {
        final ErrorResponse response = new ErrorResponse();
        response.setMessage(e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflictException(final ConflictException e) {
        final ErrorResponse response = new ErrorResponse();
        response.setMessage(e.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }
}
