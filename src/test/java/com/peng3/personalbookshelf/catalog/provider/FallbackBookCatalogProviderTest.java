package com.peng3.personalbookshelf.catalog.provider;

import com.peng3.personalbookshelf.catalog.domain.Book;
import com.peng3.personalbookshelf.catalog.provider.webcatalog.WebCatalogProviderUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class FallbackBookCatalogProviderTest {

    private static final String ISBN = "9789861375182";

    private BookCatalogProvider webCatalog;
    private BookCatalogProvider googleBooks;
    private FallbackBookCatalogProvider provider;

    @BeforeEach
    void setUp() {
        webCatalog = mock(BookCatalogProvider.class);
        googleBooks = mock(BookCatalogProvider.class);
        provider = new FallbackBookCatalogProvider(webCatalog, googleBooks);
    }

    @Test
    void shouldReturnWebCatalogResultWithoutCallingGoogle() {
        Book webCatalogResult = book("網頁書目");
        when(webCatalog.findByIsbn(ISBN)).thenReturn(Optional.of(webCatalogResult));

        Optional<Book> result = provider.findByIsbn(ISBN);

        assertThat(result).contains(webCatalogResult);
        verify(googleBooks, never()).findByIsbn(ISBN);
    }

    @Test
    void shouldUseGoogleWhenWebCatalogReturnsEmpty(CapturedOutput output) {
        Book googleResult = book("Google 書名");
        when(webCatalog.findByIsbn(ISBN)).thenReturn(Optional.empty());
        when(googleBooks.findByIsbn(ISBN)).thenReturn(Optional.of(googleResult));

        Optional<Book> result = provider.findByIsbn(ISBN);

        assertThat(result).contains(googleResult);
        verify(googleBooks).findByIsbn(ISBN);
        assertThat(output)
                .contains("網頁書目來源未回傳資料")
                .contains(ISBN);
    }

    @Test
    void shouldUseGoogleWhenWebCatalogIsUnavailable(CapturedOutput output) {
        Book googleResult = book("Google 書名");
        when(webCatalog.findByIsbn(ISBN)).thenThrow(
                new WebCatalogProviderUnavailableException(
                        "網頁書目來源暫時無法使用",
                        new RuntimeException("test")
                )
        );
        when(googleBooks.findByIsbn(ISBN)).thenReturn(Optional.of(googleResult));

        Optional<Book> result = provider.findByIsbn(ISBN);

        assertThat(result).contains(googleResult);
        verify(googleBooks).findByIsbn(ISBN);
        assertThat(output)
                .contains("網頁書目來源不可用")
                .contains("RuntimeException")
                .doesNotContain("網頁書目來源暫時無法使用")
                .doesNotContain("test");
    }

    private Book book(String title) {
        return new Book(ISBN, title, List.of(), null, null, null);
    }
}
