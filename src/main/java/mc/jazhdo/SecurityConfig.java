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
            .authorizeHttpRequests(request -> request.anyRequest().access((auth, context) -> new AuthorizationDecision(checkHost(context.getRequest()))))
            .exceptionHandling(exception -> exception.accessDeniedHandler((HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException) -> response.sendRedirect("https://1j2t0zr0-8080.usw3.devtunnels.ms/")))
            .formLogin(form -> form.disable())
            .httpBasic(basic -> basic.disable())
            .build();
    }

    public boolean checkHost(HttpServletRequest request) {
        String remoteHost = request.getRemoteHost();
        log.info("{} request recieved from {} to {}.", new Object[]{request.getMethod(), remoteHost, request.getRequestURI()});
        return true;
        // return "video.itsjaz.com".equals(remoteHost) || "127.0.0.1".equals(remoteHost) || "0:0:0:0:0:0:0:1".equals(remoteHost);
    }
}
