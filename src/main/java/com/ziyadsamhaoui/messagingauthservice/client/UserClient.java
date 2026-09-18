package com.ziyadsamhaoui.messagingauthservice.client;

import com.ziyadsamhaoui.messagingauthservice.dto.InternalRequests.CreateUserRequest;
import com.ziyadsamhaoui.messagingauthservice.exception.UserProfileSyncException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class UserClient {

    private final RestClient restClient;
    private final String internalToken;

    public UserClient(RestClient.Builder restClientBuilder,
                      @Value("${badrlink.user-service.url}") String userServiceUrl,
                      @Value("${badrlink.security.internal-token}") String internalToken) {
        this.restClient = restClientBuilder
                .baseUrl(userServiceUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
        this.internalToken = internalToken;
    }

    public void createUser(CreateUserRequest request) {
        try {
            restClient.post()
                    .uri("/internal/users")
                    .header("X-Internal-Token", internalToken)
                    .body(request)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException ex) {
            throw new UserProfileSyncException("user profile creation failed: " + ex.getMessage());
        }
    }
}
