package com.peng3.personalbookshelf.catalog.provider;

import com.peng3.personalbookshelf.catalog.domain.Book;
import com.peng3.personalbookshelf.catalog.provider.webcatalog.WebCatalogProviderUnavailableException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@Profile({"local", "real"})
@Primary
public class FallbackBookCatalogProvider implements BookCatalogProvider {

    private final BookCatalogProvider primary;
    private final BookCatalogProvider fallback;

    public FallbackBookCatalogProvider(
            @Qualifier("webCatalogProvider") BookCatalogProvider primary,
            @Qualifier("googleBooksCatalogProvider") BookCatalogProvider fallback
    ) {
        this.primary = primary;
        this.fallback = fallback;
    }

    @Override
    public Optional<Book> findByIsbn(String isbn) {
        try {
            Optional<Book> result = primary.findByIsbn(isbn);
            return result.isPresent() ? result : fallback.findByIsbn(isbn);
        } catch (WebCatalogProviderUnavailableException exception) {
            return fallback.findByIsbn(isbn);
        }
    }
}
