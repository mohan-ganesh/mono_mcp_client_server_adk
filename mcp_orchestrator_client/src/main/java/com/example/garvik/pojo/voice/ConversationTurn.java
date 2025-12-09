package com.example.garvik.pojo.voice;

public class ConversationTurn {

  private String userTranscript;
  private String geminiResponse;
  private String geminiAudioBase64;

  public ConversationTurn(String userTranscript, String geminiResponse, String geminiAudioBase64) {
    this.userTranscript = userTranscript;
    this.geminiResponse = geminiResponse;
    this.geminiAudioBase64 = geminiAudioBase64;
  }

  public String getUserTranscript() {
    return userTranscript;
  }

  public void setUserTranscript(String userTranscript) {
    this.userTranscript = userTranscript;
  }

  public String getGeminiResponse() {
    return geminiResponse;
  }

  public void setGeminiResponse(String geminiResponse) {
    this.geminiResponse = geminiResponse;
  }

  public String getGeminiAudioBase64() {
    return geminiAudioBase64;
  }
}
