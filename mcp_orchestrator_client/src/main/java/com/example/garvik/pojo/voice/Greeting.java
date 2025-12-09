package com.example.garvik.pojo.voice;

/** */
public class Greeting {

  private String content;

  private String audioBase64;

  public String getAudioBase64() {
    return audioBase64;
  }

  public void setAudioBase64(String audioBase64) {
    this.audioBase64 = audioBase64;
  }

  public Greeting(String content, String audioBase64) {
    this.content = content;
    this.audioBase64 = audioBase64;
  }

  public Greeting() {}

  public Greeting(String content) {
    this.content = content;
  }

  public String getContent() {
    return content;
  }
}
