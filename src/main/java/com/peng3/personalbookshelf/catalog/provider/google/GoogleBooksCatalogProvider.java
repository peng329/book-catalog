package com.peng3.personalbookshelf.catalog.provider.google;

import com.peng3.personalbookshelf.catalog.domain.Book;
import com.peng3.personalbookshelf.catalog.provider.BookCatalogProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Objects;
import java.util.Optional;

@Component
@Profile({"local", "real"})
public class GoogleBooksCatalogProvider implements BookCatalogProvider {

    private final RestClient restClient;
    private final GoogleBooksProperties properties;

    public GoogleBooksCatalogProvider(RestClient.Builder builder, GoogleBooksProperties properties) {
        this.restClient = builder
                .baseUrl("https://www.googleapis.com/books/v1")
                .build();
        this.properties = properties;
    }

    @Override
    public Optional<Book> findByIsbn(String isbn) {
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            throw new CatalogProviderUnavailableException("Google Books API Key 未設定");
        }

        try {
            GoogleBooksVolumesResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/volumes")
                            .queryParam("q", "isbn:" + isbn)
                            .queryParam("printType", "books")
                            .queryParam("maxResults", 10)
                            .queryParam("key", properties.apiKey())
                            .build())
                    .retrieve()
                    .body(GoogleBooksVolumesResponse.class);

            return toBook(isbn, response);
        } catch (RestClientException exception) {
            throw new CatalogProviderUnavailableException("Google Books 服務暫時無法使用", exception);
        }
    }

    private Optional<Book> toBook(String isbn, GoogleBooksVolumesResponse response) {
        if (response == null) {
            return Optional.empty();
        }

        Optional<GoogleBooksVolumesResponse.VolumeInfo> exactMatch = response.safeItems().stream()
                .map(GoogleBooksVolumesResponse.Item::volumeInfo)
                .filter(Objects::nonNull)
                .filter(volumeInfo -> volumeInfo.hasExactIsbn13(isbn))
                .findFirst();

        Optional<GoogleBooksVolumesResponse.VolumeInfo> singleSearchResult = response.hasSingleItem()
                ? response.safeItems().stream()
                .map(GoogleBooksVolumesResponse.Item::volumeInfo)
                .filter(Objects::nonNull)
                .findFirst()
                : Optional.empty();

        return exactMatch.or(() -> singleSearchResult)
                .map(volumeInfo -> new Book(
                        isbn,
                        volumeInfo.title(),
                        volumeInfo.safeAuthors(),
                        volumeInfo.publisher(),
                        volumeInfo.publishedDate(),
                        volumeInfo.description()
                ));
    }
}
