package com.xtc.assistantapi.exception;

import java.io.Serializable;

/**
 * Error raised while a device module handles a directive.
 * (Original: {@code com.xtc.assistantapi.exception.HandleDirectiveException}.)
 */
public class HandleDirectiveException extends RuntimeException implements Serializable {

    private static final long serialVersionUID = 1L;

    public enum ExceptionType {
        UNEXPECTED_INFORMATION_RECEIVED,
        UNSUPPORTED_OPERATION,
        INTERNAL_ERROR
    }

    private final ExceptionType exceptionType;

    public HandleDirectiveException(ExceptionType exceptionType, String message) {
        super(message);
        this.exceptionType = exceptionType;
    }

    public ExceptionType getExceptionType() {
        return exceptionType;
    }
}
