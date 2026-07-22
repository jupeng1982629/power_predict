package com.powerpredict.systemservice.security;

import com.powerpredict.systemservice.config.PowerPredictSystemProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class LocalAuthenticationFilter extends OncePerRequestFilter {
  private final boolean localDebugEnabled;
  private final String localToken;

  public LocalAuthenticationFilter(PowerPredictSystemProperties properties) {
    this.localDebugEnabled = properties.getAuth().isLocalDebugEnabled();
    this.localToken = properties.getAuth().getLocalToken();
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    if (!localDebugEnabled) {
      filterChain.doFilter(request, response);
      return;
    }

    String authorization = request.getHeader("Authorization");
    if (StringUtils.hasText(authorization) && authorization.startsWith("Bearer ")) {
      String token = authorization.substring(7).trim();
      if (localToken.equals(token)) {
        String userId = headerOrDefault(request, "X-User-Id", "user-demo-admin");
        String userName = headerOrDefault(request, "X-User-Name", "Demo Admin");
        String tenantId = headerOrDefault(request, "X-Tenant-Id", "tenant-demo");
        List<String> roles = Arrays.stream(headerOrDefault(request, "X-User-Roles", "forecast:read,forecast:run,system:admin,plant:read").split(","))
            .map(String::trim)
            .filter(StringUtils::hasText)
            .toList();
        List<SimpleGrantedAuthority> authorities = roles.stream()
            .map(role -> new SimpleGrantedAuthority("ROLE_" + role.replace(':', '_').toUpperCase()))
            .toList();
        DebugUserPrincipal principal = new DebugUserPrincipal(userId, userName, tenantId, roles);
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(principal, token, authorities));
      }
    }

    filterChain.doFilter(request, response);
  }

  private String headerOrDefault(HttpServletRequest request, String name, String defaultValue) {
    String value = request.getHeader(name);
    return StringUtils.hasText(value) ? value.trim() : defaultValue;
  }
}
