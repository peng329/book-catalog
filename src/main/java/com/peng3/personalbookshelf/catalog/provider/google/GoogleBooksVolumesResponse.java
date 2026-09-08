package com.peng3.personalbookshelf.catalog.provider.google;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GoogleBooksVolumesResponse(Integer totalItems, List<Item> items) {

    public List<Item> safeItems() {
        return items == null ? List.of() : items;
    }

    public boolean hasSingleItem() {
        return Integer.valueOf(1).equals(totalItems) && safeItems().size() == 1;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(VolumeInfo volumeInfo) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record VolumeInfo(
            String title,
            List<String> authors,
            String publisher,
            String publishedDate,
            String description,
            List<IndustryIdentifier> industryIdentifiers
    ) {

        public List<String> safeAuthors() {
            return authors == null ? List.of() : authors;
        }

        public boolean hasExactIsbn13(String isbn) {
            return industryIdentifiers != null && industryIdentifiers.stream()
                    .anyMatch(identifier -> "ISBN_13".equals(identifier.type())
                            && isbn.equals(identifier.identifier()));
        }

    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record IndustryIdentifier(String type, String identifier) {
    }
}
