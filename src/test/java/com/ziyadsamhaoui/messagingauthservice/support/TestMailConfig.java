package com.ziyadsamhaoui.messagingauthservice.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.mail.javamail.JavaMailSender;

@TestConfiguration(proxyBeanMethods = false)
public class TestMailConfig {

    @Bean
    JavaMailSender mailSender() {
        return new TestMailSender();
    }
}
