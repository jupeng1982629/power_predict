package com.powerpredict.gatewayservice.api;

import com.powerpredict.gatewayservice.security.DebugUserPrincipal;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class GatewayProxySupport {
  public ResponseEntity<String> getJson(String baseUrl, String pathAndQuery) {
    return exchange(baseUrl, pathAndQuery, "GET", null);
  }

  public ResponseEntity<String> postJson(String baseUrl, String pathAndQuery, String jsonBody) {
    return exchange(baseUrl, pathAndQuery, "POST", jsonBody);
  }

  public ResponseEntity<String> putJson(String baseUrl, String pathAndQuery, String jsonBody) {
    return exchange(baseUrl, pathAndQuery, "PUT", jsonBody);
  }

  public ResponseEntity<String> deleteJson(String baseUrl, String pathAndQuery) {
    return exchange(baseUrl, pathAndQuery, "DELETE", null);
  }

  public ResponseEntity<String> postMultipart(
      String baseUrl,
      String pathAndQuery,
      MultipartFile file,
      Map<String, String> formFields) {
    HttpURLConnection connection = null;
    String boundary = "----PowerPredictBoundary" + System.currentTimeMillis();

    try {
      URI uri = URI.create(baseUrl + pathAndQuery);
      connection = (HttpURLConnection) uri.toURL().openConnection();
      connection.setRequestMethod("POST");
      connection.setConnectTimeout(5000);
      connection.setReadTimeout(15000);
      connection.setDoOutput(true);
      connection.setRequestProperty(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
      connection.setRequestProperty(HttpHeaders.CONTENT_TYPE, "multipart/form-data; boundary=" + boundary);

      HttpHeaders headers = new HttpHeaders();
      applyIdentityHeaders(headers);
      for (Map.Entry<String, java.util.List<String>> entry : headers.entrySet()) {
        for (String value : entry.getValue()) {
          connection.setRequestProperty(entry.getKey(), value);
        }
      }

      try (OutputStream outputStream = connection.getOutputStream();
           PrintWriter writer = new PrintWriter(outputStream, true, StandardCharsets.UTF_8)) {

        for (Map.Entry<String, String> entry : formFields.entrySet()) {
          writer.append("--").append(boundary).append("\r\n");
          writer.append("Content-Disposition: form-data; name=\"").append(entry.getKey()).append("\"\r\n");
          writer.append("\r\n");
          writer.append(entry.getValue() == null ? "" : entry.getValue()).append("\r\n");
          writer.flush();
        }

        writer.append("--").append(boundary).append("\r\n");
        writer.append("Content-Disposition: form-data; name=\"file\"; filename=\"")
            .append(file.getOriginalFilename() == null ? "upload.xlsx" : file.getOriginalFilename())
            .append("\"\r\n");
        writer.append("Content-Type: ")
            .append(file.getContentType() == null ? "application/octet-stream" : file.getContentType())
            .append("\r\n");
        writer.append("\r\n");
        writer.flush();
        outputStream.write(file.getBytes());
        outputStream.flush();
        writer.append("\r\n");
        writer.append("--").append(boundary).append("--\r\n");
        writer.flush();
      }

      int status = connection.getResponseCode();
      InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
      String body = stream == null ? "" : new String(stream.readAllBytes(), StandardCharsets.UTF_8);
      return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(body);
    } catch (IOException exception) {
      String body = "{\"success\":false,\"code\":\"UPSTREAM_UNAVAILABLE\",\"message\":\"Upstream service is unavailable\",\"data\":null}";
      return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).contentType(MediaType.APPLICATION_JSON).body(body);
    } finally {
      if (connection != null) {
        connection.disconnect();
      }
    }
  }

  public void applyIdentityHeaders(HttpHeaders headers) {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null) {
      return;
    }

    String bearerToken = bearerToken(authentication);
    if (bearerToken != null) {
      headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken);
    }

    if (authentication.getPrincipal() instanceof DebugUserPrincipal principal) {
      headers.set("X-User-Id", principal.userId());
      headers.set("X-User-Name", principal.userName());
      headers.set("X-Tenant-Id", principal.tenantId());
      headers.set("X-User-Roles", String.join(",", principal.roles()));
      return;
    }

    if (authentication instanceof JwtAuthenticationToken jwtAuthenticationToken) {
      Jwt jwt = jwtAuthenticationToken.getToken();
      headers.set("X-User-Id", firstNonBlank(jwt.getClaimAsString("sub"), "user-unknown"));
      headers.set("X-User-Name", firstNonBlank(jwt.getClaimAsString("preferred_username"), jwt.getClaimAsString("sub"), "unknown"));
      headers.set("X-Tenant-Id", firstNonBlank(jwt.getClaimAsString("tenant_id"), "tenant-default"));

      Object realmAccess = jwt.getClaim("realm_access");
      if (realmAccess instanceof Map<?, ?> realmMap) {
        Object roles = realmMap.get("roles");
        if (roles instanceof java.util.List<?> roleList) {
          headers.set("X-User-Roles", roleList.stream().filter(Objects::nonNull).map(String::valueOf).reduce((a, b) -> a + "," + b).orElse(""));
        }
      }
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

  private String bearerToken(Authentication authentication) {
    if (authentication instanceof JwtAuthenticationToken jwtAuthenticationToken) {
      return jwtAuthenticationToken.getToken().getTokenValue();
    }

    if (authentication.getCredentials() instanceof String token && !token.isBlank()) {
      return token;
    }

    return null;
  }

  private String firstNonBlank(String... values) {
    for (String value : values) {
      if (value != null && !value.isBlank()) {
        return value;
      }
    }
    return "";
  }
}