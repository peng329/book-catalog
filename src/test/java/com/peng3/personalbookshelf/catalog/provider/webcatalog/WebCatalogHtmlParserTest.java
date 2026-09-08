package com.peng3.personalbookshelf.catalog.provider.webcatalog;

import com.peng3.personalbookshelf.catalog.domain.Book;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class WebCatalogHtmlParserTest {

    private final WebCatalogHtmlParser parser = new WebCatalogHtmlParser();

    @Test
    void shouldParseBookMetadataFromWebCatalogHtml() throws IOException {
        Optional<Book> result = parser.parse("9789861375182", sampleHtml());

        assertThat(result).hasValueSatisfying(book -> {
            assertThat(book.isbn()).isEqualTo("9789861375182");
            assertThat(book.title()).isEqualTo("原來訓練眼睛，就能強化心理素質！");
            assertThat(book.authors()).containsExactly("松島雅美 ／ 譯者：謝如欣");
            assertThat(book.publisher()).isEqualTo("究竟");
            assertThat(book.publishedDate()).isEqualTo("2026-07-01");
            assertThat(book.description()).contains("心理視覺訓練");
        });
    }

    @Test
    void shouldReturnEmptyWhenBookTitleIsMissing() {
        String html = """
                <html>
                <body>
                    <span class="tailw">出版日期：2026-07-01</span>
                </body>
                </html>
                """;

        Optional<Book> result = parser.parse("9789861375182", html);

        assertThat(result).isEmpty();
    }

    @Test
    void shouldParseDescriptionWhenJsonLdHasExtraClosingBrace() {
        String html = """
                <html>
                <body>
                    <div id="GoodsGridDiv">
                        <span class="b_name">格式異常測試書</span>
                    </div>
                    <script type="application/ld+json">
                    {"@type":"Book","description":"包含\\"引號\\"的心理視覺訓練簡介"}}
                    </script>
                </body>
                </html>
                """;

        Optional<Book> result = parser.parse("9789861375182", html);

        assertThat(result).hasValueSatisfying(book ->
                assertThat(book.description())
                        .isEqualTo("包含\"引號\"的心理視覺訓練簡介")
        );
    }

    @Test
    void shouldParseLongDescriptionWithoutOverflowingRegexStack() {
        String description = "心理視覺訓練" + "測試內容".repeat(2_000);
        String html = """
                <html>
                <body>
                    <div id="GoodsGridDiv">
                        <span class="b_name">長簡介測試書</span>
                    </div>
                    <script type="application/ld+json">
                    {"@type":"Book","description":"%s"}}
                    </script>
                </body>
                </html>
                """.formatted(description);

        Optional<Book> result = parser.parse("9789861375182", html);

        assertThat(result).hasValueSatisfying(book ->
                assertThat(book.description()).isEqualTo(description)
        );
    }

    private String sampleHtml() throws IOException {
        try (InputStream input = getClass().getResourceAsStream(
                "/webcatalog/sample-book-page.html"
        )) {
            if (input == null) {
                throw new IllegalStateException("找不到網頁書目測試 HTML");
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
