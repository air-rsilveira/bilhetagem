package com.v.challenge.lock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@ConditionalOnBean(StringRedisTemplate.class)
public class RedisLockService implements LockService {

    private final StringRedisTemplate redisTemplate;

    public RedisLockService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean tryLock(String key, Duration ttl) {
        Boolean result = redisTemplate.opsForValue()
            .setIfAbsent(key, "LOCKED", ttl);
        return Boolean.TRUE.equals(result);
    }

    @Override
    public void unlock(String key) {
        redisTemplate.delete(key);
    }
}
