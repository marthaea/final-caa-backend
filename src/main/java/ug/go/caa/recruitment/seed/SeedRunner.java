package ug.go.caa.recruitment.seed;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@Order(0)
public class SeedRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SeedRunner.class);

    private final SeedProperties properties;
    private final DatabaseSeeder seeder;
    private final Environment environment;
    private final ConfigurableApplicationContext context;

    public SeedRunner(
            SeedProperties properties,
            DatabaseSeeder seeder,
            Environment environment,
            ConfigurableApplicationContext context
    ) {
        this.properties = properties;
        this.seeder = seeder;
        this.environment = environment;
        this.context = context;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.enabled()) {
            return;
        }
        boolean production = environment.matchesProfiles("prod");
        if (production && !properties.allowProduction()) {
            throw new IllegalStateException(
                    "Refusing seed in prod profile without app.seed.allow-production=true");
        }
        if ((properties.includeDemo() || properties.includeVolume() || properties.demo())
                && production
                && !properties.allowProduction()) {
            throw new IllegalStateException("Demo/volume seed is blocked in production");
        }
        log.info(
                "Starting database seed mode={} demo={} volume={}",
                properties.mode(),
                properties.demo(),
                properties.includeVolume());
        seeder.run();
        int code = SpringApplication.exit(context, () -> 0);
        System.exit(code);
    }
}
