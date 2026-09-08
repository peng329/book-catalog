package com.peng3.personalbookshelf.catalog.api.dto;

import java.util.List;

public record BookLookupResponse(
        List<BookResponse> books
) {
}
