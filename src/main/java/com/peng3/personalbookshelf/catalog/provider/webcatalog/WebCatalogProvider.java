package com.peng3.personalbookshelf.catalog.provider.webcatalog;

import com.peng3.personalbookshelf.catalog.domain.Book;
import com.peng3.personalbookshelf.catalog.provider.BookCatalogProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@Profile({"local", "real"})
public class WebCatalogProvider implements BookCatalogProvider {

    private final WebCatalogPageLoader pageLoader;
    private final WebCatalogHtmlParser parser;

    public WebCatalogProvider(
            WebCatalogPageLoader pageLoader,
            WebCatalogHtmlParser parser
    ) {
        this.pageLoader = pageLoader;
        this.parser = parser;
    }

    @Override
    public Optional<Book> findByIsbn(String isbn) {
        return parser.parse(isbn, pageLoader.load(isbn));
    }
}
