package com.task05.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

public class EventRequest {
    private final int principalId;
    private final Map<String, Object> content;


    @JsonCreator
    public EventRequest(@JsonProperty("principalId") int principalId,
                        @JsonProperty("content") Map<String, Object> content) {
        validate(principalId, content);
        this.principalId = principalId;
        this.content = content;
    }

    public int getPrincipalId() {
        return principalId;
    }

    public Map<String, Object> getContent() {
        return content;
    }

    @Override
    public String toString() {
        return "EventRequest{" +
                "principalId=" + principalId +
                ", content=" + content +
                '}';
    }

    private void validate(int principalId, Map<String, Object> content) {
        if (principalId <= 0) {
            throw new IllegalArgumentException("Invalid principalId: Must be greater than 0");
        }
        if (content == null || content.isEmpty()) {
            throw new IllegalArgumentException("Invalid content: Must not be null or empty");
        }
    }
}
