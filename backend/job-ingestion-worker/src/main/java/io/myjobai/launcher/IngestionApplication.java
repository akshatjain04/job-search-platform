package io.myjobai.launcher;
import io.myjobai.runtime.RuntimeConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import java.util.Map;
@SpringBootApplication
@Import(RuntimeConfiguration.class)
public class IngestionApplication {
    public static void main(String[] args) { var app=new SpringApplication(IngestionApplication.class);app.setDefaultProperties(Map.of("APP_ROLE","ingestion"));app.run(args); }
}
