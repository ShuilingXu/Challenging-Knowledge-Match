package com.matrixlive.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfiguration {
  private final JwtAuthenticationFilter jwtAuthenticationFilter;
  private final ActivityAuthorizationManager activityAuthorizationManager;
  private final ActivityCreationAuthorizationManager activityCreationAuthorizationManager;

  public SecurityConfiguration(JwtAuthenticationFilter jwtAuthenticationFilter,
      ActivityAuthorizationManager activityAuthorizationManager,
      ActivityCreationAuthorizationManager activityCreationAuthorizationManager) {
    this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    this.activityAuthorizationManager = activityAuthorizationManager;
    this.activityCreationAuthorizationManager = activityCreationAuthorizationManager;
  }

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    return http.csrf(csrf -> csrf.disable())
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .formLogin(form -> form.disable())
        .httpBasic(basic -> basic.disable())
        .authorizeHttpRequests(authorize -> authorize
            .requestMatchers("/api/health", "/actuator/health", "/actuator/health/**", "/api/auth/**", "/api/site-settings").permitAll()
            // Pairing is one-time and validated against a stored hash before a scoped device JWT is issued.
            .requestMatchers(HttpMethod.POST, "/api/activities/*/screens/devices/*/session").permitAll()
            .requestMatchers(HttpMethod.GET, "/api/activities").permitAll()
            .requestMatchers(HttpMethod.GET, "/api/activities/*", "/api/activities/*/venues",
                "/api/activities/*/registration-fields").permitAll()
            .requestMatchers(HttpMethod.POST, "/api/activities/*/venues/*/registrations").permitAll()
            .requestMatchers(HttpMethod.POST, "/api/activities").access(activityCreationAuthorizationManager)
            .requestMatchers("/api/admin/**").hasRole("SYSTEM_ADMIN")
            .requestMatchers("/api/activities/**").access(activityAuthorizationManager)
            // STOMP CONNECT is authenticated by StompJwtChannelInterceptor; browser handshakes cannot set Bearer headers.
            .requestMatchers("/ws", "/ws/**").permitAll()
            .anyRequest().authenticated())
        .exceptionHandling(exceptions -> exceptions
            .authenticationEntryPoint((request, response, exception) -> writeError(response, 401, "Authentication is required"))
            .accessDeniedHandler((request, response, exception) -> writeError(response, 403, "You are not allowed to perform this action")))
        .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
        .build();
  }

  @Bean
  public PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

  /** Password authentication is performed by AuthService against user_accounts; never expose Boot's generated user. */
  @Bean
  public UserDetailsService disabledDefaultUserDetailsService() {
    return username -> { throw new UsernameNotFoundException("Use /api/auth/login"); };
  }

  private void writeError(jakarta.servlet.http.HttpServletResponse response, int status, String message)
      throws java.io.IOException {
    response.setStatus(status);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.getWriter().write("{\"error\":\"" + message + "\"}");
  }
}
