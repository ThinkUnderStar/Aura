package thinkunderstar.aura.aurabackendserver.util;

import java.util.Random;

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
}
