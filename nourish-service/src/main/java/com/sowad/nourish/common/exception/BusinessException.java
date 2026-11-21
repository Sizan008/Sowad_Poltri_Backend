package com.sowad.nourish.common.exception;

/** Expected business validation failure; messages must be safe for clients. */
public class BusinessException extends RuntimeException {
    public BusinessException(String message) {
        super(message);
    }
}
