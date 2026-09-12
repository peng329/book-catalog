package com.peng3.personalbookshelf.catalog.provider;

import com.peng3.personalbookshelf.catalog.domain.Book;
import com.peng3.personalbookshelf.catalog.provider.webcatalog.WebCatalogProviderUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@Profile({"local", "real"})
@Primary
public class FallbackBookCatalogProvider implements BookCatalogProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(FallbackBookCatalogProvider.class);

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
            if (result.isPresent()) {
                return result;
            }

            LOGGER.info("網頁書目來源未回傳資料，使用備援查詢；ISBN={}", isbn);
            return fallback.findByIsbn(isbn);
        } catch (WebCatalogProviderUnavailableException exception) {
            Throwable cause = exception.getCause();
            String failureType = cause == null
                    ? exception.getClass().getSimpleName()
                    : cause.getClass().getSimpleName();
            LOGGER.warn("網頁書目來源不可用，使用備援查詢；ISBN={}，原因類型={}", isbn, failureType);
            return fallback.findByIsbn(isbn);
        }
    }
}
