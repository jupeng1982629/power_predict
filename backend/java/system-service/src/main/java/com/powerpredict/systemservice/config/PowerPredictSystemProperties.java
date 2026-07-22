package com.powerpredict.systemservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "powerpredict")
public class PowerPredictSystemProperties {
  private final Auth auth = new Auth();

  public Auth getAuth() {
    return auth;
  }

  public static class Auth {
    private boolean localDebugEnabled = true;
    private String localToken = "local-demo-token";

    public boolean isLocalDebugEnabled() {
      return localDebugEnabled;
    }

    public void setLocalDebugEnabled(boolean localDebugEnabled) {
      this.localDebugEnabled = localDebugEnabled;
    }

    public String getLocalToken() {
      return localToken;
    }

    public void setLocalToken(String localToken) {
      this.localToken = localToken;
    }
  }
}