package com.peng3.personalbookshelf.catalog.service;

import com.peng3.personalbookshelf.catalog.api.dto.BookLookupResponse;
import com.peng3.personalbookshelf.catalog.api.dto.BookResponse;
import com.peng3.personalbookshelf.catalog.domain.Book;
import com.peng3.personalbookshelf.catalog.provider.BookCatalogProvider;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class BookLookupService {

    private final BookCatalogProvider bookCatalogProvider;

    public BookLookupService(BookCatalogProvider bookCatalogProvider) {
        this.bookCatalogProvider = bookCatalogProvider;
    }

    public BookLookupResponse lookup(List<String> isbns) {
        List<BookResponse> books = isbns.stream()
                .map(this::lookupOne)
                .toList();

        return new BookLookupResponse(books);
    }

    private BookResponse lookupOne(String isbn) {
        return bookCatalogProvider.findByIsbn(isbn)
                .map(this::toFoundResponse)
                .orElseGet(() -> toNotFoundResponse(isbn));
    }

    private BookResponse toFoundResponse(Book book) {
        return new BookResponse(
                book.isbn(),
                book.title(),
                book.authors(),
                book.publisher(),
                book.publishedDate(),
                book.description(),
                true
        );
    }

    private BookResponse toNotFoundResponse(String isbn) {
        return new BookResponse(
                isbn,
                null,
                List.of(),
                null,
                null,
                null,
                false
        );
    }
}
