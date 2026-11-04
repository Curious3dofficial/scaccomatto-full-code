package com.scaccomatto.account;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

final class SmtpEmailSender implements EmailSender {
    private final String host;
    private final int port;
    private final String username;
    private final String password;
    private final String from;

    SmtpEmailSender(String host, int port, String username, String password, String from) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
        this.from = from;
    }

    @Override
    public String sendUsernameChangeCode(
            String email,
            String currentUsername,
            String newUsername,
            String code) throws Exception {
        try (SSLSocket socket = (SSLSocket) SSLSocketFactory.getDefault().createSocket(host, port);
             BufferedReader reader = new BufferedReader(new InputStreamReader(
                     socket.getInputStream(),
                     StandardCharsets.US_ASCII));
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                     socket.getOutputStream(),
                     StandardCharsets.US_ASCII))) {
            expect(reader, 220);
            command(writer, reader, "EHLO scaccomatto", 250);
            command(writer, reader, "AUTH LOGIN", 334);
            command(writer, reader, base64(username), 334);
            command(writer, reader, base64(password), 235);
            command(writer, reader, "MAIL FROM:<" + from + ">", 250);
            command(writer, reader, "RCPT TO:<" + email + ">", 250, 251);
            command(writer, reader, "DATA", 354);

            writer.write("From: Scaccomatto <" + from + ">\r\n");
            writer.write("To: <" + email + ">\r\n");
            writer.write("Subject: Your Scaccomatto username verification code\r\n");
            writer.write("Content-Type: text/plain; charset=UTF-8\r\n");
            writer.write("\r\n");
            writer.write("Hello " + safe(currentUsername) + ",\r\n\r\n");
            writer.write("Your code to change your username to " + safe(newUsername) + " is:\r\n\r\n");
            writer.write(code + "\r\n\r\n");
            writer.write("This code expires in 10 minutes. If you did not request this, ignore this email.\r\n");
            writer.write(".\r\n");
            writer.flush();
            expect(reader, 250);
            command(writer, reader, "QUIT", 221);
        }
        return "email";
    }

    private static void command(
            BufferedWriter writer,
            BufferedReader reader,
            String command,
            int... expected) throws Exception {
        writer.write(command);
        writer.write("\r\n");
        writer.flush();
        expect(reader, expected);
    }

    private static void expect(BufferedReader reader, int... expected) throws Exception {
        String line = reader.readLine();
        if (line == null || line.length() < 3) throw new IllegalStateException("SMTP connection closed");
        int code = Integer.parseInt(line.substring(0, 3));
        while (line.length() > 3 && line.charAt(3) == '-') {
            line = reader.readLine();
            if (line == null) break;
        }
        for (int accepted : expected) {
            if (code == accepted) return;
        }
        throw new IllegalStateException("SMTP server returned " + code);
    }

    private static String base64(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String safe(String value) {
        return value.replace("\r", "").replace("\n", "");
    }
}
