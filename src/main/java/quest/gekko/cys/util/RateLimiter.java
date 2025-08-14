package quest.gekko.cys.util;

import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.Callable;
import java.util.concurrent.Semaphore;

@Component
public class RateLimiter {
    private final Semaphore sem = new Semaphore(5);
    public <T> T call(Callable<T> c) {
        try {
            sem.acquire();
            return RetryTemplate.builder().maxAttempts(3).fixedBackoff(800).build().execute(ctx -> c.call());
        } catch (Exception e) { throw new RuntimeException(e); }
        finally { sem.release(); }
    }
}