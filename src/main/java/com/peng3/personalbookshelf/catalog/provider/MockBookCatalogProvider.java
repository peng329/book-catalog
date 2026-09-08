package com.peng3.personalbookshelf.catalog.provider;

import com.peng3.personalbookshelf.catalog.domain.Book;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
@Profile("!local & !real")
public class MockBookCatalogProvider implements BookCatalogProvider {

    private final Map<String, Book> books = Map.of(
            "9786264141802",
            new Book(
                    "9786264141802",
                    "測試書籍：Kotlin 與 Android 開發",
                    List.of("測試作者"),
                    "測試出版社",
                    "2025-01-01",
                    "此為 Mock 書目資料，未來會改由外部書目服務取得。"
            )
    );

    @Override
    public Optional<Book> findByIsbn(String isbn) {
        return Optional.ofNullable(books.get(isbn));
    }
}
