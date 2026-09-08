package com.peng3.personalbookshelf.catalog.provider;

import com.peng3.personalbookshelf.catalog.provider.google.GoogleBooksProperties;
import com.peng3.personalbookshelf.catalog.provider.webcatalog.WebCatalogProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile({"local", "real"})
public class RealCatalogPropertiesValidator {

    public RealCatalogPropertiesValidator(
            GoogleBooksProperties googleBooksProperties,
            WebCatalogProperties webCatalogProperties
    ) {
        requireValue(googleBooksProperties.apiKey(), "APP_GOOGLE_BOOKS_API_KEY 未設定");
        requireValue(webCatalogProperties.baseUrl(), "CATALOG_WEB_SOURCE_BASE_URL 未設定");
    }

    private static void requireValue(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(message);
        }
    }
}
