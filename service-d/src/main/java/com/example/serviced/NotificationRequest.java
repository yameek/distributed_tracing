package com.example.serviced;

public class NotificationRequest {
    
    private String orderId;
    private String type;
    private String status;
    private String channel;
    private boolean callbackRequired;
    
    public NotificationRequest() {
    }
    
    public NotificationRequest(String orderId, String type, String status, String channel, boolean callbackRequired) {
        this.orderId = orderId;
        this.type = type;
        this.status = status;
        this.channel = channel;
        this.callbackRequired = callbackRequired;
    }
    
    public String getOrderId() {
        return orderId;
    }
    
    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }
    
    public String getType() {
        return type;
    }
    
    public void setType(String type) {
        this.type = type;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public String getChannel() {
        return channel;
    }
    
    public void setChannel(String channel) {
        this.channel = channel;
    }
    
    public boolean isCallbackRequired() {
        return callbackRequired;
    }
    
    public void setCallbackRequired(boolean callbackRequired) {
        this.callbackRequired = callbackRequired;
    }
}
