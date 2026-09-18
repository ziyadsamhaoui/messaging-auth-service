package com.ziyadsamhaoui.messagingauthservice;

import com.ziyadsamhaoui.messagingauthservice.config.AuthProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(AuthProperties.class)
public class MessagingAuthServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MessagingAuthServiceApplication.class, args);
    }
}
