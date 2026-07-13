package thinkunderstar.aura.aurabackendserver.util;

public class MailUtils {
    public static void sendCode(String email,String code){
        System.out.println("\n=============================================================");
        System.out.println("+                     验证码发送成功                           +");
        System.out.println("=============================================================");
        System.out.println("+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++");
        System.out.println("     邮箱地址:"+email+"          验证码为:"+code);
        System.out.println("+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++");
        System.out.println("=============================================================");
    }
}
