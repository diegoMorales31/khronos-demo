package com.moga.khronosdemo.exceptions;

import com.moga.khronosdemo.dtos.response.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.Map;

/**
 * A business rule was broken: 409. A rule that clients need to tell apart without reading the message
 * also carries a stable {@link ErrorCode}, and may carry details such as the day it is about; both are
 * sent in the error response, and left out when null.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class BusinessRuleException extends RuntimeException {

    private final ErrorCode code;
    private final Map<String, String> details;

    public BusinessRuleException(String message) {
        this(message, null, null);
    }

    public BusinessRuleException(String message, ErrorCode code) {
        this(message, code, null);
    }

    public BusinessRuleException(String message, ErrorCode code, Map<String, String> details) {
        super(message);
        this.code = code;
        this.details = details;
    }

    public ErrorCode getCode() {
        return code;
    }

    public Map<String, String> getDetails() {
        return details;
    }
}
