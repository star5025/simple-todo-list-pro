package org.star5025.backend.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.star5025.backend.result.Result;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Result<Void>> handleStatus(ResponseStatusException error) {
        return ResponseEntity.status(error.getStatus()).body(Result.error(error.getReason(), error.getRawStatusCode()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleValidation(MethodArgumentNotValidException error) {
        String message = error.getBindingResult().getFieldErrors().isEmpty() ? "请求参数无效"
                : error.getBindingResult().getFieldErrors().get(0).getDefaultMessage();
        return ResponseEntity.badRequest().body(Result.error(message, 400));
    }

    @ExceptionHandler({org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
            org.springframework.web.bind.MissingServletRequestParameterException.class,
            org.springframework.validation.BindException.class})
    public ResponseEntity<Result<Void>> handleParameter(Exception error) {
        return ResponseEntity.badRequest().body(Result.error("请求参数无效", 400));
    }

    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Result<Void>> handleMethod(Exception error) {
        return ResponseEntity.status(405).body(Result.error("请求方法不支持", 405));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result<Void>> handleMalformedBody(HttpMessageNotReadableException error) {
        return ResponseEntity.badRequest().body(Result.error("请求格式无效", 400));
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<Result<Void>> handleDuplicate(DuplicateKeyException error) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Result.error("记录已存在", 409));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleUnexpected(Exception error) {
        // Exception messages and stack traces can include SQL parameters or credentials.
        log.error("请求处理失败：{}", error.getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Result.error("操作失败，请稍后重试", 500));
    }
}
