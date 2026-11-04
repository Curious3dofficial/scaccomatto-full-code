package com.scaccomatto.account;

class ApiException extends Exception {
    final int status;
    final String code;

    ApiException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }
}
