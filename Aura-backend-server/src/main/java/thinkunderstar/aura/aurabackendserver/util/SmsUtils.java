package thinkunderstar.aura.aurabackendserver.util;

public class SmsUtils {

    public static void sendCode(String phone, String code) {
        System.out.println("\n=============================================================");
        System.out.println("+                     验证码发送成功                           +");
        System.out.println("=============================================================");
        System.out.println("+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++");
        System.out.println("     手机号:" + phone + "           验证码为:" + code);
        System.out.println("+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++");
        System.out.println("=============================================================");
    }
}
