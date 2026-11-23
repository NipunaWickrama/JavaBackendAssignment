package org.example.helloworld;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

/**
 * Application entry point for both deployment styles:
 * <ul>
 *   <li>{@link #main(String[])} starts the embedded Tomcat ({@code java -jar}, IntelliJ, Docker).</li>
 *   <li>{@link SpringBootServletInitializer} lets an external servlet container such as
 *       Apache Tomcat start the application when the WAR is deployed.</li>
 * </ul>
 */
@SpringBootApplication
public class HelloWorldApplication extends SpringBootServletInitializer {

    public static void main(String[] args) {
        SpringApplication.run(HelloWorldApplication.class, args);
    }

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder builder) {
        return builder.sources(HelloWorldApplication.class);
    }
}
