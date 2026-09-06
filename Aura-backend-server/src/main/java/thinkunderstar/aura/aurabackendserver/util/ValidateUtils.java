package thinkunderstar.aura.aurabackendserver.util;

/**
 * 信息验证包
 */
public class ValidateUtils {
    private static final String USERNAME_REGEX = "^[a-zA-Z][a-zA-Z0-9_]{3,15}$";
    private static final String PASSWORD_REGEX = "^(?![0-9]+$)(?![a-zA-Z]+$)[0-9A-Za-z!@#$%^&*_\\-]{8,20}$";
    private static final String PHONE_REGEX = "^1[3-9]\\d{9}$";
    private static final String EMAIL_REGEX = "^[a-zA-Z0-9_.-]+@[a-zA-Z0-9-]+(\\.[a-zA-Z0-9]+)*\\.[a-zA-Z0-9]{2,6}$";
    private static final String AGENT_NAME_REGEX = "^[\\u4e00-\\u9fa5a-zA-Z0-9_\\-\\s]{1,20}$";
    private static final String UUID_REGEX = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$";

    /**
     * Agent 名称规范验证
     * 长度：1 ~ 20 位
     * 允许：中文、字母、数字、空格、下划线、连字符
     * 不能为空，不能纯空格
     * @param name Agent 名称
     * @return 验证结果
     */
    public static boolean agentNameValidate(String name){
        return name != null && !name.trim().isEmpty() && name.matches(AGENT_NAME_REGEX);
    }

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

    /**
     * UUID 格式验证（人机验证临时 key）
     * 标准 8-4-4-4-12 的十六进制 + 连字符，与前端 crypto.randomUUID() 生成结果一致
     * @param uuid 待校验的 UUID 字符串
     * @return 是否合法
     */
    public static boolean uuidValidate(String uuid){
        return uuid != null && uuid.matches(UUID_REGEX);
    }
}
