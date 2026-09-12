package com.peng3.personalbookshelf.catalog.provider.webcatalog.selenium;

import com.peng3.personalbookshelf.catalog.provider.webcatalog.WebCatalogProperties;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.chrome.ChromeOptions;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ChromeWebCatalogWebDriverFactoryTest {

    @Test
    void shouldAddHeadlessContainerArgumentsWhenHeadlessIsEnabled() {
        ChromeOptions options = factory(true).createOptions();

        assertThat(chromeArguments(options))
                .contains(
                        "--headless=new",
                        "--no-sandbox",
                        "--disable-dev-shm-usage",
                        "--lang=zh-TW",
                        "--disable-gpu",
                        "--disable-extensions",
                        "--disable-background-networking",
                        "--disable-sync",
                        "--no-first-run",
                        "--renderer-process-limit=1",
                        "--blink-settings=imagesEnabled=false"
                );
    }

    @Test
    void shouldOnlyAddLanguageArgumentWhenHeadlessIsDisabled() {
        ChromeOptions options = factory(false).createOptions();

        assertThat(chromeArguments(options))
                .contains("--lang=zh-TW")
                .doesNotContain(
                        "--headless=new",
                        "--no-sandbox",
                        "--disable-dev-shm-usage",
                        "--renderer-process-limit=1",
                        "--blink-settings=imagesEnabled=false"
                );
    }

    private ChromeWebCatalogWebDriverFactory factory(boolean headless) {
        WebCatalogProperties properties = new WebCatalogProperties(
                "https://catalog-source.invalid",
                headless,
                Duration.ZERO,
                Duration.ofSeconds(20)
        );
        return new ChromeWebCatalogWebDriverFactory(properties);
    }

    @SuppressWarnings("unchecked")
    private List<String> chromeArguments(ChromeOptions options) {
        Map<String, Object> chromeOptions =
                (Map<String, Object>) options.getCapability(ChromeOptions.CAPABILITY);
        return (List<String>) chromeOptions.get("args");
    }
}
