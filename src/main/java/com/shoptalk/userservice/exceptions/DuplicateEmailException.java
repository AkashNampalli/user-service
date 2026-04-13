package com.shoptalk.userservice.exceptions;

public class DuplicateEmailException extends RuntimeException {

    public DuplicateEmailException(String email){
        super("Email already exists: " + email);
    }
}
