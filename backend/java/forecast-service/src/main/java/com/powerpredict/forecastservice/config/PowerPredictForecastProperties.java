package com.powerpredict.forecastservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "powerpredict")
public class PowerPredictForecastProperties {
  private final ServiceEndpoint featureService = new ServiceEndpoint("http://localhost:8001");
  private final ServiceEndpoint modelService = new ServiceEndpoint("http://localhost:8002");
  private final ServiceEndpoint inferenceService = new ServiceEndpoint("http://localhost:8003");

  public ServiceEndpoint getFeatureService() {
    return featureService;
  }

  public ServiceEndpoint getModelService() {
    return modelService;
  }

  public ServiceEndpoint getInferenceService() {
    return inferenceService;
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
}
