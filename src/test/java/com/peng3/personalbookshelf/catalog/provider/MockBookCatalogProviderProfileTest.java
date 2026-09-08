package com.peng3.personalbookshelf.catalog.provider;

import com.peng3.personalbookshelf.catalog.provider.google.GoogleBooksCatalogProvider;
import com.peng3.personalbookshelf.catalog.provider.google.GoogleBooksProperties;
import com.peng3.personalbookshelf.catalog.provider.webcatalog.WebCatalogHtmlParser;
import com.peng3.personalbookshelf.catalog.provider.webcatalog.WebCatalogProperties;
import com.peng3.personalbookshelf.catalog.provider.webcatalog.WebCatalogProvider;
import com.peng3.personalbookshelf.catalog.provider.webcatalog.selenium.ChromeWebCatalogWebDriverFactory;
import com.peng3.personalbookshelf.catalog.provider.webcatalog.selenium.SeleniumWebCatalogPageLoader;
import com.peng3.personalbookshelf.catalog.provider.webcatalog.selenium.WebCatalogWebDriverFactory;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.web.client.RestClient;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class MockBookCatalogProviderProfileTest {

    @Test
    void shouldCreateOnlyMockProviderByDefault() {
        providerContextRunner()
                .run(context -> {
                    assertThat(context).hasSingleBean(MockBookCatalogProvider.class);
                    assertThat(context.getBean(BookCatalogProvider.class))
                            .isInstanceOf(MockBookCatalogProvider.class);
                    assertThat(context).doesNotHaveBean(FallbackBookCatalogProvider.class);
                    assertThat(context).doesNotHaveBean(GoogleBooksCatalogProvider.class);
                    assertThat(context).doesNotHaveBean(WebCatalogProvider.class);
                    assertThat(context).doesNotHaveBean(WebCatalogHtmlParser.class);
                    assertThat(context).doesNotHaveBean(SeleniumWebCatalogPageLoader.class);
                    assertThat(context).doesNotHaveBean(ChromeWebCatalogWebDriverFactory.class);
                    assertThat(context).doesNotHaveBean(RealCatalogPropertiesValidator.class);
                    assertThat(context).doesNotHaveBean(GoogleBooksProperties.class);
                    assertThat(context).doesNotHaveBean(WebCatalogProperties.class);
                });
    }

    @Test
    void shouldCreateCompleteRealProviderGraphWhenLocalProfileIsActive() {
        assertCompleteRealProviderGraph("local");
    }

    @Test
    void shouldCreateCompleteRealProviderGraphWhenRealProfileIsActive() {
        assertCompleteRealProviderGraph("real");
    }

    private void assertCompleteRealProviderGraph(String profile) {
        providerContextRunner()
                .withInitializer(context -> context.getEnvironment().setActiveProfiles(profile))
                .withBean(WebCatalogProperties.class, () -> new WebCatalogProperties(
                        "https://catalog-source.invalid",
                        true,
                        Duration.ZERO,
                        Duration.ofSeconds(20)
                ))
                .withBean(GoogleBooksProperties.class, () -> new GoogleBooksProperties("test-key"))
                .run(context -> {
                    assertThat(context).doesNotHaveBean(MockBookCatalogProvider.class);
                    assertThat(context.getBean(BookCatalogProvider.class))
                            .isInstanceOf(FallbackBookCatalogProvider.class);
                    assertThat(context).hasSingleBean(FallbackBookCatalogProvider.class);
                    assertThat(context).hasSingleBean(GoogleBooksCatalogProvider.class);
                    assertThat(context).hasSingleBean(WebCatalogProvider.class);
                    assertThat(context).hasSingleBean(WebCatalogHtmlParser.class);
                    assertThat(context).hasSingleBean(SeleniumWebCatalogPageLoader.class);
                    assertThat(context).hasSingleBean(ChromeWebCatalogWebDriverFactory.class);
                    assertThat(context).hasSingleBean(WebCatalogWebDriverFactory.class);
                    assertThat(context).hasSingleBean(RealCatalogPropertiesValidator.class);
                    assertThat(context).hasSingleBean(GoogleBooksProperties.class);
                    assertThat(context).hasSingleBean(WebCatalogProperties.class);
                });
    }

    private ApplicationContextRunner providerContextRunner() {
        return new ApplicationContextRunner()
                .withBean(RestClient.Builder.class, RestClient::builder)
                .withUserConfiguration(ProviderTestConfiguration.class);
    }

    @Configuration(proxyBeanMethods = false)
    @ComponentScan(
            basePackageClasses = MockBookCatalogProvider.class,
            useDefaultFilters = false,
            includeFilters = @ComponentScan.Filter(
                    type = FilterType.ASSIGNABLE_TYPE,
                    classes = {
                            MockBookCatalogProvider.class,
                            GoogleBooksCatalogProvider.class,
                            WebCatalogHtmlParser.class,
                            ChromeWebCatalogWebDriverFactory.class,
                            SeleniumWebCatalogPageLoader.class,
                            WebCatalogProvider.class,
                            FallbackBookCatalogProvider.class,
                            RealCatalogPropertiesValidator.class
                    }
            )
    )
    static class ProviderTestConfiguration {
    }
}
