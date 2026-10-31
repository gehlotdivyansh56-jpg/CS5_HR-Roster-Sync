package com.movesync.rostersync.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class TransportFieldUpdateRequest {

    @JsonProperty("pickup_point")
    private String pickupPoint;

    @JsonProperty("drop_point")
    private String dropPoint;

    @JsonProperty("special_assistance")
    private String specialAssistance;

    public TransportFieldUpdateRequest() {}

    public TransportFieldUpdateRequest(String pickupPoint, String dropPoint, String specialAssistance) {
        this.pickupPoint = pickupPoint;
        this.dropPoint = dropPoint;
        this.specialAssistance = specialAssistance;
    }

    public String getPickupPoint() {
        return pickupPoint;
    }

    public void setPickupPoint(String pickupPoint) {
        this.pickupPoint = pickupPoint;
    }

    public String getDropPoint() {
        return dropPoint;
    }

    public void setDropPoint(String dropPoint) {
        this.dropPoint = dropPoint;
    }

    public String getSpecialAssistance() {
        return specialAssistance;
    }

    public void setSpecialAssistance(String specialAssistance) {
        this.specialAssistance = specialAssistance;
    }
}

