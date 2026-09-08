package com.peng3.personalbookshelf.catalog.provider;

import com.peng3.personalbookshelf.catalog.domain.Book;

import java.util.Optional;

public interface BookCatalogProvider {

    Optional<Book> findByIsbn(String isbn);
}
