package com.powerpredict.gatewayservice.api;

import com.powerpredict.gatewayservice.security.DebugUserPrincipal;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class GatewayProxySupport {
  public ResponseEntity<String> getJson(String baseUrl, String pathAndQuery) {
    return exchange(baseUrl, pathAndQuery, "GET", null);
  }

  public ResponseEntity<String> postJson(String baseUrl, String pathAndQuery, String jsonBody) {
    return exchange(baseUrl, pathAndQuery, "POST", jsonBody);
  }

  public void applyIdentityHeaders(HttpHeaders headers) {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null && authentication.getPrincipal() instanceof DebugUserPrincipal principal) {
      headers.add("X-User-Id", principal.userId());
      headers.add("X-User-Name", principal.userName());
      headers.add("X-Tenant-Id", principal.tenantId());
      headers.add("X-User-Roles", String.join(",", principal.roles()));
    }
  }

  public Map<String, Object> localSession() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    Map<String, Object> data = new LinkedHashMap<>();
    if (authentication != null && authentication.getPrincipal() instanceof DebugUserPrincipal principal) {
      data.put("userId", principal.userId());
      data.put("userName", principal.userName());
      data.put("tenantId", principal.tenantId());
      data.put("roles", principal.roles());
    }
    return data;
  }

  private ResponseEntity<String> exchange(String baseUrl, String pathAndQuery, String method, String jsonBody) {
    HttpURLConnection connection = null;
    try {
      URI uri = URI.create(baseUrl + pathAndQuery);
      connection = (HttpURLConnection) uri.toURL().openConnection();
      connection.setRequestMethod(method);
      connection.setConnectTimeout(5000);
      connection.setReadTimeout(15000);
      connection.setRequestProperty(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);

      HttpHeaders headers = new HttpHeaders();
      applyIdentityHeaders(headers);
      for (Map.Entry<String, java.util.List<String>> entry : headers.entrySet()) {
        for (String value : entry.getValue()) {
          connection.setRequestProperty(entry.getKey(), value);
        }
      }

      if (jsonBody != null) {
        byte[] bytes = jsonBody.getBytes(StandardCharsets.UTF_8);
        connection.setDoOutput(true);
        connection.setRequestProperty(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        connection.setRequestProperty(HttpHeaders.CONTENT_LENGTH, Integer.toString(bytes.length));
        try (OutputStream outputStream = connection.getOutputStream()) {
          outputStream.write(bytes);
        }
      }

      int status = connection.getResponseCode();
      InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
      String body = stream == null ? "" : new String(stream.readAllBytes(), StandardCharsets.UTF_8);
      return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(body);
    } catch (IOException exception) {
      String body = "{\"success\":false,\"code\":\"UPSTREAM_UNAVAILABLE\",\"message\":\"Inference service is unavailable\",\"data\":null}";
      return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).contentType(MediaType.APPLICATION_JSON).body(body);
    } finally {
      if (connection != null) {
        connection.disconnect();
      }
    }
  }
}