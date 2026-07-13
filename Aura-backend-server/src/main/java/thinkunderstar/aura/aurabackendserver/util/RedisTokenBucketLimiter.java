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
}