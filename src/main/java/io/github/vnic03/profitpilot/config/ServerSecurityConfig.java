package io.github.vnic03.profitpilot.config;

import jakarta.servlet.DispatcherType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@Profile("server")
@EnableAutoConfiguration(exclude = UserDetailsServiceAutoConfiguration.class)
public class ServerSecurityConfig {

    @Bean
    public SecurityFilterChain serverFilterChain(HttpSecurity http) throws Exception {

        // Hosted authentication belongs to Cloudflare Access.
        // Keep the origin private; these GET rules do not authenticate visitors.
        http.csrf(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(
                        auth ->
                                auth.dispatcherTypeMatchers(DispatcherType.ERROR)
                                        .permitAll()
                                        // Authentication for these internal routes is enforced by Cloudflare Access.
                                        // The origin must not be directly accessible.
                                        .requestMatchers(
                                            HttpMethod.GET,
                                            "/api/operations/build",
                                            "/api/operations/imports",
                                            "/api/finance/import-budget",
                                            "/api/news/budget",
                                            "/api/finance/data-status")
                                        .permitAll()
                                        // Public Api Routes
                                        .requestMatchers(
                                                HttpMethod.GET,
                                                "/api/finance/stocks",
                                                "/api/finance/winnersAndLosers/*",
                                                "/api/finance/chart/*",
                                                "/api/finance/dynamicSMA/*/*",
                                                "/api/finance/dynamicEMA/*/*",
                                                "/api/finance/dynamicRSI/*/*",
                                                "/api/finance/export/*",
                                                "/api/news")
                                        .permitAll()
                                        .requestMatchers(HttpMethod.HEAD, "/api/operations/build")
                                        .permitAll()
                                        .requestMatchers("/api", "/api/**")
                                        .denyAll()
                                        .requestMatchers(HttpMethod.GET, "/**")
                                        .permitAll()
                                        .requestMatchers(HttpMethod.HEAD, "/**")
                                        .permitAll()
                                        .anyRequest()
                                        .denyAll());

        return http.build();
    }
}
