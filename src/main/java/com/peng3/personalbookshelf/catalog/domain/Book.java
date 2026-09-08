package com.peng3.personalbookshelf.catalog.domain;

import java.util.List;

public record Book(
        String isbn,
        String title,
        List<String> authors,
        String publisher,
        String publishedDate,
        String description
) {
}
