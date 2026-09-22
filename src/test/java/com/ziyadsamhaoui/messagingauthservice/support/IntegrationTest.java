package com.ziyadsamhaoui.messagingauthservice.support;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({IntegrationTestContainers.class, TestMailConfig.class})
@ActiveProfiles("test")
public abstract class IntegrationTest {

    protected TestUserServer userServer;

    @Autowired
    protected TestMailSender mailSender;

    @LocalServerPort
    protected int port;

    protected final RestTemplate restTemplate = passThroughRestTemplate();

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected StringRedisTemplate redisTemplate;

    @BeforeEach
    void resetMailSender() {
        mailSender.reset();
    }

    @BeforeEach
    void startUserServer() throws IOException {
        userServer = new TestUserServer(18082);
    }

    @AfterEach
    void stopUserServer() {
        if (userServer != null) {
            userServer.close();
        }
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
        jdbcTemplate.update("delete from refresh_tokens");
        jdbcTemplate.update("delete from password_reset_tokens");
        jdbcTemplate.update("delete from credentials");
    }

    protected void register(String email) {
        register(email, "SecurePassword123!");
    }

    protected void register(String email, String password) {
        restTemplate.postForEntity(
                url("/auth/register"), jsonEntity(java.util.Map.of(
                        "email", email,
                        "username", "user_" + email.split("@")[0].replace("-", "_"),
                        "password", password)), java.util.Map.class);
    }

    protected String url(String path) {
        return "http://localhost:" + port + path;
    }

    protected HttpEntity<java.util.Map<String, String>> jsonEntity(java.util.Map<String, String> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    protected String fetchLatestResetTokenRaw() {
        String body = mailSender.lastSentBody()
                .orElseThrow(() -> new IllegalStateException("no reset email was sent"));
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("token=([A-Za-z0-9_-]+)")
                .matcher(body);
        if (!matcher.find()) {
            throw new IllegalStateException("reset link not found in email body: " + body);
        }
        return matcher.group(1);
    }

    private static RestTemplate passThroughRestTemplate() {
        RestTemplate template = new RestTemplate();
        template.setErrorHandler(new ResponseErrorHandler() {
            @Override
            public boolean hasError(ClientHttpResponse response) {
                return false;
            }
        });
        return template;
    }
}
