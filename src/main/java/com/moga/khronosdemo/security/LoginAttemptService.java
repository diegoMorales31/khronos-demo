package com.moga.khronosdemo.security;

public interface LoginAttemptService {

    /**
     * @return how many seconds the pair must wait before trying again, because the pair, its IP or its
     * username is blocked, or 0 if it may try now. A username's block does not hold back an IP it
     * signed in from successfully not long ago.
     */
    long secondsUntilAllowed(String username, String clientIp);

    void recordFailure(String username, String clientIp);

    void recordSuccess(String username, String clientIp);
}
