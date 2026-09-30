package com.ziyadsamhaoui.messagingauthservice.outbox;

import tools.jackson.databind.ObjectMapper;


public interface OutboxPublisher {

    void publish(String aggregateType, String aggregateId, String eventType, Object payload);

    String topicFor(String eventType);

    ObjectMapper objectMapper();
}
