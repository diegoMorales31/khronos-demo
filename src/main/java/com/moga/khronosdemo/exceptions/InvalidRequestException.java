package com.moga.khronosdemo.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.Map;

/**
 * The request can't be carried out as sent: 400. When the fault is in given fields, which only the service
 * can tell (for example a stock count's difference from the stock at that moment), it carries them as a
 * failed validation does, and they are sent as fieldErrors.
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidRequestException extends RuntimeException {

    private final Map<String, String> fieldErrors;

    public InvalidRequestException(String message) {
        this(message, null);
    }

    public InvalidRequestException(String message, Map<String, String> fieldErrors) {
        super(message);
        this.fieldErrors = fieldErrors;
    }

    public Map<String, String> getFieldErrors() {
        return fieldErrors;
    }
}
