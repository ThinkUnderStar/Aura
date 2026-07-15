package thinkunderstar.aura.aurabackendserver.common;

import lombok.Data;

/**
 * 统一返回结果
 * 后端给前端的标准格式：code + msg + data
 */
@Data
public class Result<T> {

    private int code;    // 状态码 200=成功 500=失败
    private String msg; // 提示信息
    private T data; // 数据

    // ===================== 成功返回 =====================
    public static <T> Result<T> success() {
        Result<T> result = new Result<>();
        result.setCode(200);
        result.setMsg("操作成功");
        return result;
    }

    public static <T> Result<T> success(T data) {
        Result<T> result = new Result<>();
        result.setCode(200);
        result.setMsg("操作成功");
        result.setData(data);
        return result;
    }

    // ===================== 失败返回 =====================
    public static <T> Result<T> error(String msg) {
        Result<T> result = new Result<>();
        result.setCode(500);
        result.setMsg(msg);
        return result;
    }

    public static <T> Result<T> error(int code, String msg) {
        Result<T> result = new Result<>();
        result.setCode(code);
        result.setMsg(msg);
        return result;
    }

    public static <T> Result<T> error(int code, String msg, T data) {
        Result<T> result = new Result<>();
        result.setCode(code);
        result.setMsg(msg);
        result.setData(data);
        return result;
    }
}