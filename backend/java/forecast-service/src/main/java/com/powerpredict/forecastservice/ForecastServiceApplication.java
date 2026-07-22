package com.powerpredict.forecastservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ForecastServiceApplication {
  public static void main(String[] args) {
    SpringApplication.run(ForecastServiceApplication.class, args);
  }
}
