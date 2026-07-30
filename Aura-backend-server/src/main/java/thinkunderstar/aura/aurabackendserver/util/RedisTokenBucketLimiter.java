package thinkunderstar.aura.aurabackendserver.util;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.ReturnType;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
public class RedisTokenBucketLimiter {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private static final String TOKEN_BUCKET_SCRIPT =
            "local key = KEYS[1] " +
                    "local capacity = tonumber(ARGV[1]) " +
                    "local rate = tonumber(ARGV[2]) " +
                    "local now = tonumber(ARGV[3]) " +
                    "local requested = tonumber(ARGV[4]) " +
                    "local last_tokens = tonumber(redis.call('hget', key, 'tokens') or capacity) " +
                    "local last_refresh = tonumber(redis.call('hget', key, 'refresh_time') or now) " +
                    "local delta = math.max(0, (now - last_refresh) * rate) " +
                    "local new_tokens = math.min(capacity, last_tokens + delta) " +
                    "local allowed = new_tokens >= requested " +
                    "if allowed then " +
                    "    new_tokens = new_tokens - requested " +
                    "end " +
                    "redis.call('hset', key, 'tokens', new_tokens) " +
                    "redis.call('hset', key, 'refresh_time', now) " +
                    "redis.call('expire', key, math.ceil(capacity / rate) + 10) " +
                    "return allowed and 1 or 0";

    public boolean tryAcquireByUser(String userId, long capacity, long rate) {
        return tryAcquire("rate:user:" + userId, capacity, rate, 1);
    }

    public boolean tryAcquireByIp(String ip, long capacity, long rate) {
        return tryAcquire("rate:ip:" + ip, capacity, rate, 1);
    }

    public boolean tryAcquireGlobal(String api, long capacity, long rate) {
        return tryAcquire("rate:global:" + api, capacity, rate, 1);
    }

    public boolean tryAcquire(String key, long capacity, long rate, int requested) {
        long now = System.currentTimeMillis() / 1000;

        byte[] scriptBytes = TOKEN_BUCKET_SCRIPT.getBytes(StandardCharsets.UTF_8);
        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);

        Long result = redisTemplate.execute((RedisCallback<Long>) connection -> {
            return connection.eval(
                    scriptBytes,
                    ReturnType.INTEGER,
                    1,
                    keyBytes,
                    String.valueOf(capacity).getBytes(StandardCharsets.UTF_8),
                    String.valueOf(rate).getBytes(StandardCharsets.UTF_8),
                    String.valueOf(now).getBytes(StandardCharsets.UTF_8),
                    String.valueOf(requested).getBytes(StandardCharsets.UTF_8)
            );
        });

        return result != null && result == 1L;
    }

    public double getUserTokens(String userId) {
        Object tokens = redisTemplate.opsForHash().get("rate:user:" + userId, "tokens");
        if (tokens == null) {
            return -1;
        }
        return Double.parseDouble(tokens.toString());
    }

    public void resetUser(String userId) {
        redisTemplate.delete("rate:user:" + userId);
    }

    // ===================== 新增：支持 double 类型的 rate =====================

    /**
     * 按用户限流（支持小数速率）
     * @param userId   用户标识
     * @param capacity 桶容量（突发数）
     * @param rate     每秒补充的令牌数（支持小数，如 0.1 表示每10秒补充1个）
     * @return true 允许通过，false 被限流
     */
    public boolean tryAcquireByUser(String userId, long capacity, double rate) {
        return tryAcquire("rate:user:" + userId, capacity, rate, 1);
    }

    /**
     * 按 IP 限流（支持小数速率）
     */
    public boolean tryAcquireByIp(String ip, long capacity, double rate) {
        return tryAcquire("rate:ip:" + ip, capacity, rate, 1);
    }

    /**
     * 按全局 API 限流（支持小数速率）
     */
    public boolean tryAcquireGlobal(String api, long capacity, double rate) {
        return tryAcquire("rate:global:" + api, capacity, rate, 1);
    }

    /**
     * 核心令牌桶方法（支持小数速率）
     * @param key       限流键
     * @param capacity  桶容量
     * @param rate      每秒补充令牌数（支持小数）
     * @param requested 本次请求消耗的令牌数，通常为 1
     * @return true 允许，false 拒绝
     */
    public boolean tryAcquire(String key, long capacity, double rate, int requested) {
        if (rate <= 0) {
            throw new IllegalArgumentException("rate must be positive");
        }
        long now = System.currentTimeMillis() / 1000;
        byte[] scriptBytes = TOKEN_BUCKET_SCRIPT.getBytes(StandardCharsets.UTF_8);
        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);

        Long result = redisTemplate.execute((RedisCallback<Long>) connection -> {
            return connection.eval(
                    scriptBytes,
                    ReturnType.INTEGER,
                    1,
                    keyBytes,
                    String.valueOf(capacity).getBytes(StandardCharsets.UTF_8),
                    String.valueOf(rate).getBytes(StandardCharsets.UTF_8),  // rate 传入字符串，Lua 中 tonumber 可解析
                    String.valueOf(now).getBytes(StandardCharsets.UTF_8),
                    String.valueOf(requested).getBytes(StandardCharsets.UTF_8)
            );
        });
        return result != null && result == 1L;
    }
}