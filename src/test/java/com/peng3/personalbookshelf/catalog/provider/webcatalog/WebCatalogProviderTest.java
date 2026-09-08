package com.peng3.personalbookshelf.catalog.provider.webcatalog;

import com.peng3.personalbookshelf.catalog.domain.Book;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class WebCatalogProviderTest {

    @Test
    void shouldReturnParsedBookFromLoadedPage() {
        WebCatalogPageLoader pageLoader = isbn -> sampleHtml();
        WebCatalogProvider provider = new WebCatalogProvider(
                pageLoader,
                new WebCatalogHtmlParser()
        );

        Optional<Book> result = provider.findByIsbn("9789861375182");

        assertThat(result).hasValueSatisfying(book -> {
            assertThat(book.isbn()).isEqualTo("9789861375182");
            assertThat(book.title()).isEqualTo("原來訓練眼睛，就能強化心理素質！");
        });
    }

    @Test
    void shouldReturnEmptyWhenLoadedPageHasNoBookTitle() {
        WebCatalogPageLoader pageLoader = isbn -> "<html><body></body></html>";
        WebCatalogProvider provider = new WebCatalogProvider(
                pageLoader,
                new WebCatalogHtmlParser()
        );

        Optional<Book> result = provider.findByIsbn("9789861375182");

        assertThat(result).isEmpty();
    }

    private String sampleHtml() {
        try (InputStream input = getClass().getResourceAsStream(
                "/webcatalog/sample-book-page.html"
        )) {
            if (input == null) {
                throw new IllegalStateException("找不到網頁書目測試 HTML");
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("讀取網頁書目測試 HTML 失敗", exception);
        }
    }
}
