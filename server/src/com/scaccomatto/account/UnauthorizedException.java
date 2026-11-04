package com.scaccomatto.account;

final class UnauthorizedException extends ApiException {
    UnauthorizedException(String code, String message) {
        super(403, code, message);
    }
}
