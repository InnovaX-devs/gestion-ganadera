package com.innovax.ganadera;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

// Se excluye el usuario en memoria que Spring crea por defecto: la autenticación será por JWT (M1).
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class GanaderaApplication {

    public static void main(String[] args) {
        SpringApplication.run(GanaderaApplication.class, args);
    }
}
