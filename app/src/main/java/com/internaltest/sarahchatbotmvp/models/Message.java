package com.internaltest.sarahchatbotmvp.models;

public class Message {

  private final String message;
  private final boolean isReceived;

  public Message(String message, boolean isReceived) {
    this.message = message;
    this.isReceived = isReceived;
  }

  public String getMessage() {
    return message;
  }

  public boolean getIsReceived() {
    return isReceived;
  }

}
