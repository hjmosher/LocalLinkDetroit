package com.locallink.granite;

/** Thrown by IamTokenService when the token exchange fails. */
public class IamTokenException extends RuntimeException {
    public IamTokenException(String message) { super(message); }
    public IamTokenException(String message, Throwable cause) { super(message, cause); }
}
