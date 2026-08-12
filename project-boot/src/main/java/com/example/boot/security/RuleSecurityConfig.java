package com.example.boot.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import javax.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;

@Configuration
public class RuleSecurityConfig {

    @Bean
    public SecurityFilterChain ruleSecurityFilterChain(HttpSecurity http,
                                                       RuleSecurityProperties properties,
                                                       RuleApiKeyAuthenticationFilter apiKeyFilter) throws Exception {
        http.csrf().disable();
        http.cors();
        http.sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS);
        http.httpBasic().disable();
        http.formLogin().disable();
        http.logout().disable();

        if (!properties.isEnabled()) {
            http.authorizeRequests().anyRequest().permitAll();
            return http.build();
        }

        http.addFilterBefore(apiKeyFilter, UsernamePasswordAuthenticationFilter.class);
        http.exceptionHandling()
                .authenticationEntryPoint((request, response, exception) -> writeError(
                        response, HttpServletResponse.SC_UNAUTHORIZED, "authentication_required"))
                .accessDeniedHandler((request, response, exception) -> writeError(
                        response, HttpServletResponse.SC_FORBIDDEN, "insufficient_permission"));

        http.authorizeRequests()
                .antMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .antMatchers("/actuator/health", "/actuator/info").permitAll()
                .antMatchers(HttpMethod.GET, "/api/rules/**").hasAnyRole("READER", "ADMIN")
                .antMatchers("/api/rules/**").hasRole("ADMIN")
                .anyRequest().permitAll();

        return http.build();
    }

    private static void writeError(HttpServletResponse response, int status, String message) {
        try {
            response.setStatus(status);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write("{\"code\":" + status + ",\"message\":\"" + message + "\",\"data\":null}");
        } catch (Exception ignored) {
            response.setStatus(status);
        }
    }
}
