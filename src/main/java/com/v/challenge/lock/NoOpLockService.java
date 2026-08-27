package com.v.challenge.lock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Implementação no-op do LockService para ambientes sem Redis (ex: profile test).
 * Sempre adquire o lock com sucesso.
 */
@Service
@ConditionalOnMissingBean(StringRedisTemplate.class)
public class NoOpLockService implements LockService {

    @Override
    public boolean tryLock(String key, Duration ttl) {
        return true;
    }

    @Override
    public void unlock(String key) {
        // no-op
    }
}
