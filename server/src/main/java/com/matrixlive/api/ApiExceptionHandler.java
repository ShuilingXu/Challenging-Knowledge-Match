package com.matrixlive.api;

import com.matrixlive.service.DomainException;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
  @ExceptionHandler(DomainException.class)
  public ResponseEntity<Map<String, Object>> domain(DomainException exception) {
    return ResponseEntity.status(exception.getStatus()).body(Map.of("error", exception.getMessage(), "time", Instant.now().toString()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<Map<String, Object>> validation(MethodArgumentNotValidException exception) {
    String message = exception.getBindingResult().getFieldErrors().stream().findFirst()
        .map(error -> error.getField() + " " + error.getDefaultMessage()).orElse("请求参数不合法");
    return ResponseEntity.badRequest().body(Map.of("error", message, "time", Instant.now().toString()));
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<Map<String, Object>> integrity(DataIntegrityViolationException exception) {
    return ResponseEntity.status(409).body(Map.of("error", "The request conflicts with an existing record",
        "time", Instant.now().toString()));
  }
}
