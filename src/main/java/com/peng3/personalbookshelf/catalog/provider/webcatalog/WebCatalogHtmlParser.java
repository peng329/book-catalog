package com.peng3.personalbookshelf.catalog.provider.webcatalog;

import com.peng3.personalbookshelf.catalog.domain.Book;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Optional;

@Component
@Profile({"local", "real"})
public class WebCatalogHtmlParser {

    private static final JsonMapper JSON_MAPPER = JsonMapper.shared();
    private static final String DESCRIPTION_KEY = "\"description\"";

    public Optional<Book> parse(String isbn, String html) {
        Document document = Jsoup.parse(html);
        String title = elementText(document.selectFirst("#GoodsGridDiv .b_name"));
        if (title == null) {
            return Optional.empty();
        }

        String author = labeledText(document, ".tailISBN", "作者：");
        String publisher = labeledText(document, ".tailISBN", "出版社：");
        String publishedDate = labeledText(document, ".tailw", "出版日期：");
        String description = descriptionFromJsonLd(document);

        return Optional.of(new Book(
                isbn,
                title,
                author == null ? List.of() : List.of(author),
                publisher,
                publishedDate,
                description
        ));
    }

    private String labeledText(Document document, String selector, String label) {
        return document.select(selector).stream()
                .map(Element::text)
                .filter(text -> text.startsWith(label))
                .map(text -> nullableText(text.substring(label.length())))
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private String descriptionFromJsonLd(Document document) {
        for (Element script : document.select("script[type=application/ld+json]")) {
            try {
                JsonNode root = JSON_MAPPER.readTree(script.data());
                JsonNode description = root == null ? null : root.get("description");
                if (description != null && description.isString()) {
                    String value = nullableText(description.asString());
                    if (value != null) {
                        return value;
                    }
                }
            } catch (JacksonException ignored) {
                String description = descriptionFromMalformedJson(script.data());
                if (description != null) {
                    return description;
                }
            }
        }
        return null;
    }

    private String descriptionFromMalformedJson(String json) {
        int searchFrom = 0;
        while (searchFrom < json.length()) {
            int keyStart = json.indexOf(DESCRIPTION_KEY, searchFrom);
            if (keyStart < 0) {
                return null;
            }

            int valueStart = skipWhitespace(json, keyStart + DESCRIPTION_KEY.length());
            if (valueStart < json.length() && json.charAt(valueStart) == ':') {
                valueStart = skipWhitespace(json, valueStart + 1);
                if (valueStart < json.length() && json.charAt(valueStart) == '"') {
                    int valueEnd = findJsonStringEnd(json, valueStart);
                    if (valueEnd > valueStart) {
                        return decodeJsonString(json.substring(valueStart, valueEnd + 1));
                    }
                }
            }

            searchFrom = keyStart + DESCRIPTION_KEY.length();
        }
        return null;
    }

    private int skipWhitespace(String value, int start) {
        int index = start;
        while (index < value.length() && Character.isWhitespace(value.charAt(index))) {
            index++;
        }
        return index;
    }

    private int findJsonStringEnd(String json, int openingQuote) {
        boolean escaped = false;
        for (int index = openingQuote + 1; index < json.length(); index++) {
            char character = json.charAt(index);
            if (escaped) {
                escaped = false;
            } else if (character == '\\') {
                escaped = true;
            } else if (character == '"') {
                return index;
            }
        }
        return -1;
    }

    private String decodeJsonString(String jsonString) {
        try {
            JsonNode description = JSON_MAPPER.readTree(jsonString);
            return description != null && description.isString()
                    ? nullableText(description.asString())
                    : null;
        } catch (JacksonException ignored) {
            return null;
        }
    }

    private String elementText(Element element) {
        return element == null ? null : nullableText(element.text());
    }

    private String nullableText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.strip();
    }
}
