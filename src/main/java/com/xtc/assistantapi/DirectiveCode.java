package com.xtc.assistantapi;

/**
 * HTTP-like dispatch codes of the assistant directive entry point.
 * (Original: {@code com.xtc.assistantapi.c.d}, {@code DirectiveCode}.)
 */
public interface DirectiveCode {
    int OK = 200;
    int PARTIAL = 300;
    int BAD_REQUEST = 400;
    int FORBIDDEN = 403;
    int NOT_FOUND = 404;
    int INTERNAL_ERROR = 500;
}
