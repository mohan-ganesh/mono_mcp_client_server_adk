package com.example.garvik.config;

import com.google.cloud.texttospeech.v1.SsmlVoiceGender;

/** Constants used across the ADK Client Application. */
public class Constants {
  public static final String AGENT_NAME = "mohan-ganesh";
  public static final String USER_NAME = "user-123";
  public static final String AUTHORIZATION_KEY = "Authorization";
  public static String UID_KEY = "uid";
  public static String GIVEN_NAME_KEY = "givenname";
  public static String SIR_NAME_KEY = "sn";
  public static String EMAIL_KEY = "mail";
  public static String USER_ID_KEY = "userId";

  public static String inputSpeechModel = "inputSpeechRecgnozitationModel";
  public static String responsePitch = "ttsPitch";
  public static String responseSpeakingRate = "ttsSpeakingRate";
  public static String responseVoiceName = "ttsVoiceName";

  public enum SpeechModel {
    LATEST_LONG("latest_long"),
    LATEST_SHORT("latest_short"),
    TELEPHONY("telephony"),
    MEDICAL_DICTATION("medical_dictation");

    private final String modelName;

    SpeechModel(String modelName) {
      this.modelName = modelName;
    }

    public String getModelName() {
      return modelName;
    }
  }

  public enum TtsVoice {
    FEMALE_WAVENET_C("en-US-Wavenet-C", SsmlVoiceGender.FEMALE),
    FEMALE_STANDARD_H("en-US-Standard-H", SsmlVoiceGender.FEMALE),
    MALE_WAVENET_A("en-US-Wavenet-A", SsmlVoiceGender.MALE),
    MALE_STANDARD_I("en-US-Standard-I", SsmlVoiceGender.MALE),
    MALE_WAVENET_B("en-US-Wavenet-B", SsmlVoiceGender.MALE),
    MALE_WAVENET_D("en-US-Wavenet-D", SsmlVoiceGender.MALE);

    private final String voiceName;
    private final SsmlVoiceGender gender;

    TtsVoice(String voiceName, SsmlVoiceGender gender) {
      this.voiceName = voiceName;
      this.gender = gender;
    }

    public String getVoiceName() {
      return voiceName;
    }

    public SsmlVoiceGender getGender() {
      return gender;
    }
  }
}
