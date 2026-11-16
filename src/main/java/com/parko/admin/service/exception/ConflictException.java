package com.parko.admin.service.exception;

public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
