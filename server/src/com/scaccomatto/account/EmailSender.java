package com.scaccomatto.account;

interface EmailSender {
    String sendUsernameChangeCode(String email, String username, String newUsername, String code)
            throws Exception;
}
