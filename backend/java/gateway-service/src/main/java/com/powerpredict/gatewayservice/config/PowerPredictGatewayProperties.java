package com.powerpredict.gatewayservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "powerpredict")
public class PowerPredictGatewayProperties {
  private final ServiceEndpoint systemService = new ServiceEndpoint("http://localhost:8081");
  private final ServiceEndpoint plantService = new ServiceEndpoint("http://localhost:8082");
  private final ServiceEndpoint monitorService = new ServiceEndpoint("http://localhost:8084");
  private final ServiceEndpoint forecastService = new ServiceEndpoint("http://localhost:8085");
  private final ServiceEndpoint inferenceService = new ServiceEndpoint("http://localhost:8003");
  private final Auth auth = new Auth();

  public ServiceEndpoint getSystemService() {
    return systemService;
  }

  public ServiceEndpoint getInferenceService() {
    return inferenceService;
  }

  public ServiceEndpoint getPlantService() {
    return plantService;
  }

  public ServiceEndpoint getMonitorService() {
    return monitorService;
  }

  public ServiceEndpoint getForecastService() {
    return forecastService;
  }

  public Auth getAuth() {
    return auth;
  }

  public static class ServiceEndpoint {
    private String baseUrl;

    public ServiceEndpoint() {
    }

    public ServiceEndpoint(String baseUrl) {
      this.baseUrl = baseUrl;
    }

    public String getBaseUrl() {
      return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
      this.baseUrl = baseUrl;
    }
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