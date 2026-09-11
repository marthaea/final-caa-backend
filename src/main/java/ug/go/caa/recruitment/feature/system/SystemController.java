package ug.go.caa.recruitment.feature.system;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SystemController {

    @GetMapping("/ping")
    Map<String, Object> ping() {
        return Map.of("ok", true, "ts", System.currentTimeMillis());
    }
}
