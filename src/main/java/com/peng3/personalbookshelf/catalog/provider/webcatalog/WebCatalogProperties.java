package com.peng3.personalbookshelf.catalog.provider.webcatalog;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties(prefix = "catalog.web-source")
public record WebCatalogProperties(
        String baseUrl,
        @DefaultValue("true") boolean headless,
        @DefaultValue("2s") Duration requestDelay,
        @DefaultValue("20s") Duration pageTimeout
) {
}
