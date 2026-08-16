package com.matrixlive.security;

import com.matrixlive.security.JwtTokenService.TokenClaims;
import com.matrixlive.security.auth.UserAccount;
import com.matrixlive.security.auth.UserAccountRepository;
import com.matrixlive.security.auth.UserRole;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
  private final JwtTokenService tokens;
  private final TokenRevocationService revocations;
  private final UserAccountRepository users;

  public JwtAuthenticationFilter(JwtTokenService tokens, TokenRevocationService revocations, UserAccountRepository users) {
    this.tokens = tokens;
    this.revocations = revocations;
    this.users = users;
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String header = request.getHeader("Authorization");
    if (header == null || !header.startsWith("Bearer ")) {
      chain.doFilter(request, response);
      return;
    }
    try {
      TokenClaims claims = tokens.parse(header.substring(7));
      if (revocations.isAccessTokenRevoked(claims.tokenId())) {
        unauthorized(response, "Access token has been revoked");
        return;
      }
      if (claims.kind() == PrincipalKind.ACCOUNT && !isEnabledAccount(claims)) {
        unauthorized(response, "Account is unavailable");
        return;
      }
      AuthenticatedPrincipal principal = new AuthenticatedPrincipal(claims.tokenId(), claims.kind(), claims.userId(),
          claims.participantId(), claims.deviceId(), claims.activityId(), claims.role(), claims.username(), claims.expiresAt());
      var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + authorityFor(claims.role())));
      SecurityContextHolder.getContext().setAuthentication(
          new UsernamePasswordAuthenticationToken(principal, null, authorities));
      chain.doFilter(request, response);
    } catch (JwtException | IllegalArgumentException exception) {
      SecurityContextHolder.clearContext();
      unauthorized(response, "Invalid or expired access token");
    }
  }

  private String authorityFor(UserRole role) { return role.name(); }

  private boolean isEnabledAccount(TokenClaims claims) {
    return claims.userId() != null && users.findById(claims.userId())
        .filter(UserAccount::isEnabled)
        // A global role change takes effect immediately instead of waiting for token expiry.
        .map(account -> account.getSystemRole() == claims.role()
            || (account.getSystemRole() == null && claims.role() == UserRole.STAFF))
        .orElse(false);
  }

  private void unauthorized(HttpServletResponse response, String message) throws IOException {
    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.getWriter().write("{\"error\":\"" + message + "\"}");
  }
}
