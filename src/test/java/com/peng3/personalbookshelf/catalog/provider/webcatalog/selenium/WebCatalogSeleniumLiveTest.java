package com.peng3.personalbookshelf.catalog.provider.webcatalog.selenium;

import com.peng3.personalbookshelf.catalog.provider.webcatalog.WebCatalogProperties;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class WebCatalogSeleniumLiveTest {

    private static final String ISBN = "9789861375182";

    @Test
    @EnabledIfSystemProperty(named = "web-catalog.live-test", matches = "true")
    void shouldLoadRealWebCatalogPage() throws Exception {
        String baseUrl = System.getProperty("catalog.web-source.base-url");
        Assumptions.assumeTrue(
                baseUrl != null && !baseUrl.isBlank(),
                "catalog.web-source.base-url 未設定"
        );
        boolean headless = Boolean.parseBoolean(
                System.getProperty("catalog.web-source.headless", "true")
        );
        WebCatalogProperties properties = new WebCatalogProperties(
                baseUrl,
                headless,
                Duration.ZERO,
                Duration.ofSeconds(20)
        );
        Path output = Path.of("target", "web-catalog-sample.html");

        try (SeleniumWebCatalogPageLoader loader = new SeleniumWebCatalogPageLoader(
                new ChromeWebCatalogWebDriverFactory(properties),
                properties
        )) {
            String html = loader.load(ISBN);
            Files.createDirectories(output.getParent());
            Files.writeString(output, html, StandardCharsets.UTF_8);

            assertThat(html).containsAnyOf(ISBN, "原來訓練眼睛");
        }
    }
}
