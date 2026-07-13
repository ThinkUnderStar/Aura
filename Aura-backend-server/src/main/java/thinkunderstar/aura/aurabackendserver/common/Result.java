package thinkunderstar.aura.aurabackendserver.common;

import lombok.Data;

/**
 * 统一返回结果
 * 后端给前端的标准格式：code + msg + data
 */
@Data
public class Result {

    private int code;    // 状态码 200=成功 500=失败
    private String msg; // 提示信息
    private Object data; // 数据

    // ===================== 成功返回 =====================
    public static Result success() {
        Result result = new Result();
        result.setCode(200);
        result.setMsg("操作成功");
        return result;
    }

    public static Result success(Object data) {
        Result result = new Result();
        result.setCode(200);
        result.setMsg("操作成功");
        result.setData(data);
        return result;
    }

    // ===================== 失败返回 =====================
    public static Result error(String msg) {
        Result result = new Result();
        result.setCode(500);
        result.setMsg(msg);
        return result;
    }

    public static Result error(int code, String msg) {
        Result result = new Result();
        result.setCode(code);
        result.setMsg(msg);
        return result;
    }

    public static Result error(int code, String msg, Object data) {
        Result result = new Result();
        result.setCode(code);
        result.setMsg(msg);
        result.setData(data);
        return result;
    }
}