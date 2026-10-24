package com.ziyadsamhaoui.messagingauthservice;

import com.ziyadsamhaoui.messagingauthservice.config.AuthProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableConfigurationProperties(AuthProperties.class)
@EnableScheduling
public class MessagingAuthServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MessagingAuthServiceApplication.class, args);
    }
}
