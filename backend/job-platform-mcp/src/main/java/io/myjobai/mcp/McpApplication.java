package io.myjobai.mcp;

import io.myjobai.runtime.RuntimeConfiguration;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import(RuntimeConfiguration.class)
public class McpApplication {
  public static void main(String[] args) {
    var app = new SpringApplication(McpApplication.class);
    app.setDefaultProperties(Map.of("APP_ROLE", "mcp"));
    app.run(args);
  }
}
