package com.peng3.personalbookshelf.catalog.provider.google;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.google-books")
public record GoogleBooksProperties(String apiKey) {
}
