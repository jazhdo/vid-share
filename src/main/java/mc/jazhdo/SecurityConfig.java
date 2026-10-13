package mc.jazhdo;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity 
public class SecurityConfig {
    @Bean 
    public SecurityFilterChain securityFilterChain(HttpSecurity security) {
        return security.csrf(csrf -> csrf.disable()).authorizeHttpRequests(request -> request.anyRequest().permitAll()).formLogin(form -> form.disable()).httpBasic(basic -> basic.disable()).build();
    }
}
