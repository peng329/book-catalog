package com.peng3.personalbookshelf.catalog.provider;

import com.peng3.personalbookshelf.catalog.provider.google.GoogleBooksProperties;
import com.peng3.personalbookshelf.catalog.provider.webcatalog.WebCatalogProperties;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RealCatalogPropertiesValidatorTest {

    @Test
    void shouldRejectBlankGoogleApiKeyWithoutExposingItsValue() {
        assertThatThrownBy(() -> new RealCatalogPropertiesValidator(
                new GoogleBooksProperties("   "),
                validWebCatalogProperties()
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("APP_GOOGLE_BOOKS_API_KEY 未設定");
    }

    @Test
    void shouldRejectNullWebBaseUrlWithoutExposingItsValue() {
        assertThatThrownBy(() -> new RealCatalogPropertiesValidator(
                new GoogleBooksProperties("test-key"),
                webCatalogProperties(null)
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("CATALOG_WEB_SOURCE_BASE_URL 未設定");
    }

    @Test
    void shouldRejectBlankWebBaseUrlWithoutExposingItsValue() {
        assertThatThrownBy(() -> new RealCatalogPropertiesValidator(
                new GoogleBooksProperties("test-key"),
                webCatalogProperties("   ")
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("CATALOG_WEB_SOURCE_BASE_URL 未設定");
    }

    @Test
    void shouldAcceptCompleteRealCatalogConfiguration() {
        assertThatNoException().isThrownBy(() -> new RealCatalogPropertiesValidator(
                new GoogleBooksProperties("test-key"),
                validWebCatalogProperties()
        ));
    }

    private WebCatalogProperties validWebCatalogProperties() {
        return webCatalogProperties("https://catalog-source.invalid");
    }

    private WebCatalogProperties webCatalogProperties(String baseUrl) {
        return new WebCatalogProperties(
                baseUrl,
                true,
                Duration.ZERO,
                Duration.ofSeconds(20)
        );
    }
}
