package com.example.garvik.pojo.voice;

public class AudioMessage {
  private String audioData;
  private String mimeType;
  private String textData;
  private String sessionId;

  // Constructors, getters, and setters
  public String getSessionId() {
    return sessionId;
  }

  public void setSessionId(String sessionId) {
    this.sessionId = sessionId;
  }

  public AudioMessage() {}

  public AudioMessage(String audioData, String mimeType) {
    this.audioData = audioData;
    this.mimeType = mimeType;
  }

  public String getAudioData() {
    return audioData;
  }

  public void setAudioData(String audioData) {
    this.audioData = audioData;
  }

  public String getMimeType() {
    return mimeType;
  }

  public void setMimeType(String mimeType) {
    this.mimeType = mimeType;
  }

  public String getTextData() {
    return textData;
  }

  public void setTextData(String textData) {
    this.textData = textData;
  }
}
