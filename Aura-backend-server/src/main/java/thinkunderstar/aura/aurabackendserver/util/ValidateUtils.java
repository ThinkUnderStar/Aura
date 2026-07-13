package thinkunderstar.aura.aurabackendserver.util;

/**
 * 信息验证包
 */
public class ValidateUtils {
    private static final String USERNAME_REGEX = "^[a-zA-Z][a-zA-Z0-9_]{3,15}$";
    private static final String PASSWORD_REGEX = "^(?![0-9]+$)(?![a-zA-Z]+$)[0-9A-Za-z!@#$%^&*_\\-]{8,20}$";
    private static final String PHONE_REGEX = "^1[3-9]\\d{9}$";
    private static final String EMAIL_REGEX = "^[a-zA-Z0-9_.-]+@[a-zA-Z0-9-]+(\\.[a-zA-Z0-9]+)*\\.[a-zA-Z0-9]{2,6}$";

    /**
     * 用户名规范验证
     * 长度：4 ~ 16 位
     * 只能：字母、数字、下划线
     * 必须以字母开头
     * 不能纯数字，不能有特殊符号、空格、中文
     * @param username 用户输入的用户名
     * @return 验证结果
     */
    public static boolean usernameValidate(String username){
        return username != null && username.matches(USERNAME_REGEX);
    }

    /**
     * 用户密码规范验证
     * 长度 8 ~ 20 位
     * 必须包含 字母 + 数字
     * 可允许：! @ # $ % ^ & * - _ 等安全符号
     * 天然防 HTML / XSS 注入（禁止 < > ' " \ 等危险字符）
     *  @param password 用户输入的密码
     *  @return 验证结果
     */
    public static boolean passwordValidate(String password){
        return password != null && password.matches(PASSWORD_REGEX);
    }

    /**
     * 手机号验证
     * @param phone 用户输入的手机号
     * @return 验证结果
     */
    public static boolean phoneValidate(String phone){
        return phone != null && phone.matches(PHONE_REGEX);
    }

    /**
     * 邮箱验证
     * @param email 用户输入的邮箱地址
     * @return 验证结果
     */
    public static boolean emailValidate(String email){
        return email != null && email.matches(EMAIL_REGEX);
    }
}
