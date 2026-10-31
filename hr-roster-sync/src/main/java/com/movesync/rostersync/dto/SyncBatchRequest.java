package com.movesync.rostersync.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.ArrayList;
import java.util.List;

public class SyncBatchRequest {

    @NotEmpty(message = "events list cannot be empty")
    @Valid
    @JsonProperty("events")
    private List<SyncRecordDto> events = new ArrayList<>();

    public SyncBatchRequest() {}

    public SyncBatchRequest(List<SyncRecordDto> events) {
        this.events = events;
    }

    public List<SyncRecordDto> getEvents() {
        return events;
    }

    public void setEvents(List<SyncRecordDto> events) {
        this.events = events;
    }
}

