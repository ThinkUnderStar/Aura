package thinkunderstar.aura.aurabackendserver.util;

import jakarta.servlet.http.HttpServletRequest;

/**
 * IP 获取工具类
 * 支持代理、负载均衡场景下的真实 IP 获取
 */
public class IpUtils {

    private static final String UNKNOWN = "unknown";
    private static final String LOCALHOST_IPV4 = "127.0.0.1";
    private static final String LOCALHOST_IPV6 = "0:0:0:0:0:0:0:1";

    /**
     * 获取客户端真实 IP
     *
     * 获取顺序：
     * 1. X-Forwarded-For（Squid/Nginx 代理）
     * 2. Proxy-Client-IP（Apache 代理）
     * 3. WL-Proxy-Client-IP（WebLogic 代理）
     * 4. X-Real-IP（Nginx 代理）
     * 5. RemoteAddr（直连）
     */
    public static String getClientIp(HttpServletRequest request) {
        if (request == null) {
            return LOCALHOST_IPV4;
        }

        String ip = null;

        // 1. X-Forwarded-For
        ip = request.getHeader("X-Forwarded-For");
        if (isValidIp(ip)) {
            // 多级代理时，取第一个非 unknown 的 IP
            int index = ip.indexOf(',');
            if (index != -1) {
                ip = ip.substring(0, index);
            }
            return ip.trim();
        }

        // 2. Proxy-Client-IP
        ip = request.getHeader("Proxy-Client-IP");
        if (isValidIp(ip)) {
            return ip.trim();
        }

        // 3. WL-Proxy-Client-IP
        ip = request.getHeader("WL-Proxy-Client-IP");
        if (isValidIp(ip)) {
            return ip.trim();
        }

        // 4. X-Real-IP
        ip = request.getHeader("X-Real-IP");
        if (isValidIp(ip)) {
            return ip.trim();
        }

        // 5. RemoteAddr
        ip = request.getRemoteAddr();
        if (LOCALHOST_IPV6.equals(ip)) {
            ip = LOCALHOST_IPV4;
        }
        return ip;
    }

    /**
     * 判断 IP 是否有效（非空且非 unknown）
     */
    private static boolean isValidIp(String ip) {
        return ip != null && !ip.isEmpty() && !UNKNOWN.equalsIgnoreCase(ip);
    }
}