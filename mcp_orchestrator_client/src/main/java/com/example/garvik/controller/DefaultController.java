package com.example.garvik.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DefaultController {

  @GetMapping("/healthcheck")
  public String home() {
    return "Welcome to ADK Client Application!";
  }
}
