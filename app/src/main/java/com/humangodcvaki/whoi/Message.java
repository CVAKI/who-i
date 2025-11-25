package com.humangodcvaki.whoi;

import com.google.firebase.firestore.IgnoreExtraProperties;

@IgnoreExtraProperties // Ignore any extra fields from Firestore
public class Message {

    private String senderId;
    private String senderName;
    private String text;
    private long timestamp;

    // Required empty constructor for Firebase
    public Message() {}

    public Message(String senderId, String senderName, String text, long timestamp) {
        this.senderId = senderId;
        this.senderName = senderName;
        this.text = text;
        this.timestamp = timestamp;
    }

    // Getters
    public String getSenderId() {
        return senderId != null ? senderId : "";
    }

    public String getSenderName() {
        return senderName != null ? senderName : "Unknown";
    }

    public String getText() {
        return text != null ? text : "";
    }

    public long getTimestamp() {
        return timestamp;
    }

    // Setters
    public void setSenderId(String senderId) {
        this.senderId = senderId;
    }

    public void setSenderName(String senderName) {
        this.senderName = senderName;
    }

    public void setText(String text) {
        this.text = text;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    // Helper method to check if this is a system message
    public boolean isSystemMessage() {
        return "system".equals(senderId);
    }
}