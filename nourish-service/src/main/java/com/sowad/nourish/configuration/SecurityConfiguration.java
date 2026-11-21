package com.sowad.nourish.configuration;

import com.sowad.nourish.authentication.SessionAuthenticationService;
import com.sowad.nourish.common.security.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SecurityConfiguration {
  @Bean
  UserDetailsService unusedDefaultLogin() {
    return username -> {
      throw new UsernameNotFoundException("Use /auth/login");
    };
  }

  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      SessionAuthenticationService sessions,
      SecurityErrorWriter errors,
      @org.springframework.beans.factory.annotation.Value("${springdoc.api-docs.enabled:false}")
          boolean swaggerEnabled)
      throws Exception {
    return http.cors(cors -> {})
        // This API accepts Authorization headers only, never authentication cookies.
        .csrf(csrf -> csrf.disable())
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .requestCache(cache -> cache.disable())
        .formLogin(form -> form.disable())
        .httpBasic(basic -> basic.disable())
        .logout(logout -> logout.disable())
        .authorizeHttpRequests(
            auth -> {
              auth.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll();
              if (swaggerEnabled) {
                auth.requestMatchers(
                        HttpMethod.GET,
                        "/",
                        "/swagger-ui.html",
                        "/swagger-ui/**",
                        "/v3/api-docs",
                        "/v3/api-docs/**",
                        "/v3/api-docs.yaml")
                    .permitAll();
              } else {
                auth.requestMatchers(
                        "/swagger-ui.html",
                        "/swagger-ui/**",
                        "/v3/api-docs",
                        "/v3/api-docs/**",
                        "/v3/api-docs.yaml")
                    .denyAll();
              }
              auth.requestMatchers(HttpMethod.GET, "/health")
                  .permitAll()
                  .requestMatchers(HttpMethod.POST, "/auth/login")
                  .permitAll()
                  .anyRequest()
                  .authenticated();
            })
        .exceptionHandling(
            handler ->
                handler
                    .authenticationEntryPoint(
                        (req, res, ex) ->
                            errors.write(res, 401, "UNAUTHORIZED", "Authentication required."))
                    .accessDeniedHandler(
                        (req, res, ex) ->
                            errors.write(res, 403, "FORBIDDEN", "Permission denied.")))
        .addFilterBefore(
            new BearerAuthenticationFilter(sessions, errors),
            UsernamePasswordAuthenticationFilter.class)
        .build();
  }
}
