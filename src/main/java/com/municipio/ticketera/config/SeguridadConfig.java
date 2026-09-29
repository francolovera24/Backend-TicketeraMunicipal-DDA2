package com.municipio.ticketera.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security para una API REST con JWT: sin sesion, sin CSRF (no hay
 * cookies), sin login por formulario ni basic auth. La autenticacion la arma
 * JwtAuthenticationFilter; la autorizacion por rol se declara en cada endpoint
 * con @PreAuthorize.
 */
@Configuration
@EnableWebSecurity
public class SeguridadConfig {

    /** BCrypt: las passwords nunca se guardan en texto plano. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain cadenaDeSeguridad(HttpSecurity http, ProveedorJwt proveedorJwt,
                                                 RespuestaDeError respuestaDeError) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a.anyRequest().permitAll())
                .addFilterBefore(new JwtAuthenticationFilter(proveedorJwt, respuestaDeError),
                        UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
