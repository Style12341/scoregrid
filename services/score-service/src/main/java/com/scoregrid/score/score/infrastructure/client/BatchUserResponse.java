package com.scoregrid.score.score.infrastructure.client;

import com.fasterxml.jackson.annotation.JsonProperty;

record BatchUserResponse(
        @JsonProperty("id") String id,
        @JsonProperty("username") String username
) {}
