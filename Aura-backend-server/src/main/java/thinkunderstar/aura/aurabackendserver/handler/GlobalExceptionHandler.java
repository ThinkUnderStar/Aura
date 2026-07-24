package thinkunderstar.aura.aurabackendserver.handler;

import cn.dev33.satoken.exception.NotLoginException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import thinkunderstar.aura.aurabackendserver.common.Result;
import thinkunderstar.aura.aurabackendserver.exception.AlreadyExistsException;
import thinkunderstar.aura.aurabackendserver.exception.AuthException;
import thinkunderstar.aura.aurabackendserver.exception.BusinessException;

import java.nio.charset.StandardCharsets;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AuthException.class)
    public ResponseEntity<?> handlerAuthException(AuthException e) {
        log.error("认证异常", e);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Result.error(401, e.getMessage()));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<?> handlerBusinessException(BusinessException e, HttpServletRequest request) {
        String accept = request.getHeader(HttpHeaders.ACCEPT);
        log.error("业务异常", e);

        // 如果是下载接口（期望返回二进制流），则返回错误文本文件
        if (accept != null && accept.contains("application/octet-stream")) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"error.txt\"")
                    .body(e.getMessage().getBytes(StandardCharsets.UTF_8));
        }

        // 普通接口返回 JSON 格式的 Result
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.error(500, e.getMessage()));
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<?> handlerDuplicateKeyException(DuplicateKeyException e) {
        log.error("数据已存在", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.error(500, "该数据已存在"));
    }

    @ExceptionHandler(NotLoginException.class)
    public ResponseEntity<?> handlerNotLoginException(NotLoginException e) {
        log.warn("用户未登录", e);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Result.error(401, "用户未登录"));
    }

    @ExceptionHandler(AlreadyExistsException.class)
    public ResponseEntity<?> handlerAlreadyExistsException(AlreadyExistsException e) {
        log.error("数据已存在", e);
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Result.error(409, e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleException(Exception e) {
        log.error("服务异常", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.error(500, "服务器内部错误，请稍后再试"));
    }
}