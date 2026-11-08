package mc.jazhdo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Configuration
@EnableWebSecurity 
public class SecurityConfig {
    private final Logger log = LoggerFactory.getLogger(VidShareApplication.class);

    @Bean 
    public SecurityFilterChain securityFilterChain(HttpSecurity security) {
        return security
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(request -> request.anyRequest().access((auth, context) -> {
                assert context != null;
                HttpServletRequest servletRequest = context.getRequest();
                log.info("{} request recieved from {} to {}.", servletRequest.getMethod(), servletRequest.getRemoteHost(), servletRequest.getRequestURI());
                return new AuthorizationDecision(true);
            }))
            .exceptionHandling(exception -> exception.accessDeniedHandler((HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException) -> response.sendRedirect("https://video.itsjaz.com")))
            .formLogin(login -> login.disable())
            .httpBasic(http -> http.disable())
            .build();
    }
}
