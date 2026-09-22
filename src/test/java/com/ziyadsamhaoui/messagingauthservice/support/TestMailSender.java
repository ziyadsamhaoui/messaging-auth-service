package com.ziyadsamhaoui.messagingauthservice.support;

import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import jakarta.mail.Session;
import java.io.InputStream;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

public class TestMailSender implements JavaMailSender {

    private final AtomicInteger sent = new AtomicInteger();
    private volatile boolean failing;
    private volatile String lastBody;

    @Override
    public MimeMessage createMimeMessage() {
        return new MimeMessage((Session) null);
    }

    @Override
    public MimeMessage createMimeMessage(InputStream contentStream) {
        return new MimeMessage((Session) null);
    }

    @Override
    public void send(MimeMessage mimeMessage) throws MailSendException {
        if (failing) {
            throw new MailSendException("smtp unavailable");
        }
        lastBody = extractBody(mimeMessage);
        sent.incrementAndGet();
    }

    private static String extractBody(MimeMessage mimeMessage) {
        try {
            Object content = mimeMessage.getContent();
            return content != null ? content.toString() : null;
        } catch (Exception ex) {
            return null;
        }
    }

    @Override
    public void send(MimeMessage... mimeMessages) throws MailSendException {
        for (MimeMessage message : mimeMessages) {
            send(message);
        }
    }

    @Override
    public void send(SimpleMailMessage simpleMessage) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void send(SimpleMailMessage... simpleMessages) {
        throw new UnsupportedOperationException();
    }

    public void failNextSends() {
        failing = true;
    }

    public void succeed() {
        failing = false;
    }

    public Optional<String> lastSentBody() {
        return Optional.ofNullable(lastBody);
    }

    public void reset() {
        sent.set(0);
        failing = false;
        lastBody = null;
    }

    public int sentCount() {
        return sent.get();
    }
}
