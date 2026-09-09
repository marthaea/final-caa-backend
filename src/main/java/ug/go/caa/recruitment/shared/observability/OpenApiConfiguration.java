package ug.go.caa.recruitment.shared.observability;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {

    @Bean
    OpenAPI recruitmentOpenApi() {
        return new OpenAPI().info(new Info()
                .title("CAA Recruitment API")
                .version("0.1.0")
                .description("Java migration foundation; business endpoints remain on the existing runtime."));
    }
}
