package thinkunderstar.aura.aurabackendserver.handler;

import cn.dev33.satoken.exception.NotLoginException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import thinkunderstar.aura.aurabackendserver.common.Result;
import thinkunderstar.aura.aurabackendserver.exception.AlreadyExistsException;
import thinkunderstar.aura.aurabackendserver.exception.AuthException;
import thinkunderstar.aura.aurabackendserver.exception.BusinessException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理认证异常。
     *
     * @param e 认证异常对象
     * @return 返回包含错误码401和异常信息的Result对象
     */
    @ExceptionHandler(AuthException.class)
    public Result handlerAuthException(AuthException e){
        log.error("认证异常",e);
        return Result.error(401,e.getMessage());
    }

    /**
     * 处理业务异常。
     *
     * @param e 业务异常对象
     * @return 返回包含错误码500和异常信息的Result对象
     */
    @ExceptionHandler(BusinessException.class)
    public Result handlerBusinessException(BusinessException e){
        log.error("业务异常",e);
        return Result.error(500,e.getMessage());
    }

    /**
     * 处理数据库中键重复异常。
     *
     * @return 返回包含错误码500和"该用户已存在"信息的Result对象
     */
    @ExceptionHandler(DuplicateKeyException.class)
    public Result handlerDuplicateKeyException(DuplicateKeyException e){
        log.error("该用户已存在",e);
        return Result.error(500,"该数据已存在");
    }

    /**
     * 处理未登录异常。
     *
     * @param e 未登录异常对象
     * @return 返回包含错误码500和"用户未登录"信息的Result对象
     */
    @ExceptionHandler(NotLoginException.class)
    public Result handlerNotLoginException(NotLoginException e){
        log.warn("用户未登录",e);
        return Result.error(401,"用户未登录");
    }

    /**
     * 处理数据已存在异常。
     *
     * @param e 数据已存在异常对象
     * @return 返回包含错误码409和异常信息的Result对象
     */
    @ExceptionHandler(AlreadyExistsException.class)
    public Result handlerAlreadyExistsException(AlreadyExistsException e){
        log.error("数据已存在", e);
        return Result.error(409, e.getMessage());
    }

    /**
     * 处理全局未捕获的异常。
     *
     * @param e 异常对象
     * @return 返回包含错误码500和"服务器异常"信息的Result对象
     */
    @ExceptionHandler(Exception.class)
    public Result handleException(Exception e){
        log.error("服务异常",e);
        return Result.error(500, "服务器内部错误，请稍后再试");
    }
}
