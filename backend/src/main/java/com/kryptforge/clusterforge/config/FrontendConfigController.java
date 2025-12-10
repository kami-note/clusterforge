package com.kryptforge.clusterforge.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.CrossOrigin;

@RestController
@RequestMapping("/api/config/public")
@CrossOrigin(origins = "*") // Allow frontend to fetch config easily
public class FrontendConfigController {

    @Value("${clusterforge.access.host:}")
    private String accessHost;

    @Value("${clusterforge.access.protocol:http}")
    private String accessProtocol;

    @GetMapping
    public FrontendConfig getConfig() {
        // Return null/empty string if not configured, allowing frontend to fallback
        return new FrontendConfig(
                accessHost.isBlank() ? null : accessHost,
                accessProtocol);
    }
}
