package thinkunderstar.aura.aurabackendserver.util;

/**
 * 关键信息脱敏工具包
 */
public class DesensitizeUtils {
    //手机号脱敏
    public static String desensitizePhone(String source){
        StringBuilder stringBuilder = new StringBuilder(source);
        return stringBuilder.replace(3, 7, "****").toString();
    }

    //邮箱脱敏
    public static String desensitizeEmail(String source){
        return source.replaceAll("(\\w)\\w+@", "$1***@");
    }

}
