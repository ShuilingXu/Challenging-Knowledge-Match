package com.matrixlive.security.auth;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

@Component
public class RequestMetadata {
  public String ipAddress(HttpServletRequest request) {
    return request == null ? null : truncate(request.getRemoteAddr(), 64);
  }

  public String userAgent(HttpServletRequest request) {
    return request == null ? null : truncate(request.getHeader("User-Agent"), 512);
  }

  private String truncate(String value, int maxLength) {
    if (value == null) return null;
    return value.length() <= maxLength ? value : value.substring(0, maxLength);
  }
}
