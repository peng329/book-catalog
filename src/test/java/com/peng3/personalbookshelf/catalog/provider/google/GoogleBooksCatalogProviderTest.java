package com.peng3.personalbookshelf.catalog.provider.google;

import com.peng3.personalbookshelf.catalog.domain.Book;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class GoogleBooksCatalogProviderTest {

    private MockRestServiceServer server;
    private GoogleBooksCatalogProvider provider;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        provider = new GoogleBooksCatalogProvider(builder, new GoogleBooksProperties("test-key"));
    }

    @Test
    void shouldMapBookOnlyWhenGoogleReturnsExactIsbn13() {
        server.expect(requestTo(startsWith("https://www.googleapis.com/books/v1/volumes?")))
                .andExpect(method(GET))
                .andExpect(queryParam("q", "isbn:9786264141802"))
                .andExpect(queryParam("printType", "books"))
                .andExpect(queryParam("maxResults", "10"))
                .andExpect(queryParam("key", "test-key"))
                .andRespond(withSuccess("""
                        {
                          "items": [{
                            "volumeInfo": {
                              "title": "Android 開發實戰",
                              "authors": ["王小明"],
                              "publisher": "測試出版社",
                              "publishedDate": "2025-01-01",
                              "description": "測試簡介",
                              "industryIdentifiers": [
                                {"type": "ISBN_13", "identifier": "9786264141802"}
                              ]
                            }
                          }]
                        }
                        """, MediaType.APPLICATION_JSON));

        Optional<Book> result = provider.findByIsbn("9786264141802");

        assertThat(result).hasValueSatisfying(book -> {
            assertThat(book.isbn()).isEqualTo("9786264141802");
            assertThat(book.title()).isEqualTo("Android 開發實戰");
            assertThat(book.authors()).containsExactly("王小明");
            assertThat(book.publisher()).isEqualTo("測試出版社");
            assertThat(book.publishedDate()).isEqualTo("2025-01-01");
            assertThat(book.description()).isEqualTo("測試簡介");
        });
        server.verify();
    }

    @Test
    void shouldReturnEmptyWhenGoogleResultDoesNotContainExactIsbn13() {
        server.expect(requestTo(startsWith("https://www.googleapis.com/books/v1/volumes?")))
                .andRespond(withSuccess("""
                        {
                          "items": [{
                            "volumeInfo": {
                              "title": "相似但不是同一本書",
                              "industryIdentifiers": [
                                {"type": "ISBN_13", "identifier": "9789573293343"}
                              ]
                            }
                          }]
                        }
                        """, MediaType.APPLICATION_JSON));

        Optional<Book> result = provider.findByIsbn("9786264141802");

        assertThat(result).isEmpty();
        server.verify();
    }

    @Test
    void shouldMapUniqueGoogleIsbnSearchResultWhenMetadataOmitsIsbn() {
        server.expect(requestTo(startsWith("https://www.googleapis.com/books/v1/volumes?")))
                .andRespond(withSuccess("""
                        {
                          "totalItems": 1,
                          "items": [{
                            "volumeInfo": {
                              "title": "軟體設計耦合的平衡之道",
                              "publishedDate": "2025",
                              "industryIdentifiers": [
                                {"type": "OTHER", "identifier": "OCLC:1524135171"}
                              ]
                            }
                          }]
                        }
                        """, MediaType.APPLICATION_JSON));

        Optional<Book> result = provider.findByIsbn("9786264141802");

        assertThat(result).hasValueSatisfying(book -> {
            assertThat(book.isbn()).isEqualTo("9786264141802");
            assertThat(book.title()).isEqualTo("軟體設計耦合的平衡之道");
            assertThat(book.publishedDate()).isEqualTo("2025");
        });
        server.verify();
    }

    @Test
    void shouldReturnEmptyWhenMultipleGoogleResultsOmitExactIsbn() {
        server.expect(requestTo(startsWith("https://www.googleapis.com/books/v1/volumes?")))
                .andRespond(withSuccess("""
                        {
                          "totalItems": 2,
                          "items": [
                            {"volumeInfo": {"title": "第一個候選結果"}},
                            {"volumeInfo": {"title": "第二個候選結果"}}
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        Optional<Book> result = provider.findByIsbn("9786264141802");

        assertThat(result).isEmpty();
        server.verify();
    }

    @Test
    void shouldFailExplicitlyWhenGoogleRateLimitsRequest() {
        server.expect(requestTo(startsWith("https://www.googleapis.com/books/v1/volumes?")))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> provider.findByIsbn("9786264141802"))
                .isInstanceOf(CatalogProviderUnavailableException.class);
    }

    @Test
    void shouldFailExplicitlyWhenGoogleReturnsServerError() {
        server.expect(requestTo(startsWith("https://www.googleapis.com/books/v1/volumes?")))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> provider.findByIsbn("9786264141802"))
                .isInstanceOf(CatalogProviderUnavailableException.class);
    }
}
