package com.v.challenge.lock;

import com.v.challenge.exception.LockIndisponivelException;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.function.Supplier;

@Component
public class LockExecutor {

    private final LockService lockService;

    public LockExecutor(LockService lockService) {
        this.lockService = lockService;
    }

    public <T> T executeWithLock(String lockKey, Duration ttl, Supplier<T> supplier) {
        if (!lockService.tryLock(lockKey, ttl)) {
            throw new LockIndisponivelException("Geração de cobrança em andamento.");
        }

        try {
            return supplier.get();
        } finally {
            lockService.unlock(lockKey);
        }
    }
}
