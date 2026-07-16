package thinkunderstar.aura.aurabackendserver.util;

import java.util.Random;
import java.util.UUID;

/**
 * 获取验证码
 */
public class CodeUtils {
    static Random r = new Random();

    //六位随机数字验证码
    public static String getSixDigitCode(){
        return String.format("%06d", r.nextInt(1000000));
    }

    //四位随机字符验证码
    public static String getFourCharCode() {
        String chars = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            int index = r.nextInt(chars.length());
            sb.append(chars.charAt(index));
        }
        return sb.toString();
    }

    /**
     * 生成团队邀请码
     * 格式：aura-{UUID}，如 aura-550e8400e29b41d4a716446655440000
     */
    public static String generateInviteCode() {
        return "aura-" + UUID.randomUUID().toString().replace("-", "");
    }
}
