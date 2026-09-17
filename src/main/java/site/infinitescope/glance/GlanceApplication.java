package site.infinitescope.glance;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.nio.file.Files;
import java.nio.file.Path;

@SpringBootApplication
@EnableScheduling
@EnableAsync
@ConfigurationPropertiesScan
public class GlanceApplication {

    public static void main(String[] args) throws Exception {
        createParentDir(System.getenv().getOrDefault("GLANCE_DB_PATH", "./data/glance.db"));
        createParentDir(System.getenv().getOrDefault("GLANCE_LOG_PATH", "./logs/glance-of-tech.log"));
        SpringApplication.run(GlanceApplication.class, args);
    }

    private static void createParentDir(String path) throws Exception {
        Path parent = Path.of(path).toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }
}
