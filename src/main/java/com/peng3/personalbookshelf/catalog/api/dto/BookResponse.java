package com.peng3.personalbookshelf.catalog.api.dto;

import java.util.List;

public record BookResponse(
        String isbn,
        String title,
        List<String> authors,
        String publisher,
        String publishedDate,
        String description,
        boolean found
) {
}
