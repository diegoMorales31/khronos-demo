package com.moga.khronosdemo.security;

public interface LoginAttemptService {

    /**
     * @return how many seconds the pair must wait before trying again, because the pair or its IP is
     * blocked, or 0 if it may try now
     */
    long secondsUntilAllowed(String username, String clientIp);

    void recordFailure(String username, String clientIp);

    void recordSuccess(String username, String clientIp);
}
