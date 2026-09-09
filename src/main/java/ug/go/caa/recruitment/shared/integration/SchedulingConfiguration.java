package ug.go.caa.recruitment.shared.integration;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@Profile("!test & !seed")
public class SchedulingConfiguration {
}
