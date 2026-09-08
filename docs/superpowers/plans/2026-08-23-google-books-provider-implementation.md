# Google Books Provider Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在啟用 `local` Profile 時，使用 Google Books API 依 ISBN 查詢真實書目；預設與測試環境仍使用 Mock Provider。

**Architecture:** `BookCatalogProvider` 介面維持不變，透過 Spring Profile 決定注入 Mock 或 Google Provider。Google Provider 只負責外部 HTTP 呼叫、精確 ISBN 比對與映射領域 `Book`；外部服務故障會拋出專用例外，並由全域 Web 例外處理器轉成 HTTP 502。

**Tech Stack:** Java 17、Spring Boot 4.1、Spring RestClient、ConfigurationProperties、JUnit 5、MockRestServiceServer、MockMvc。

---

## 檔案結構與責任

~~~text
book-catalog-api/
├─ src/main/java/com/peng3/personalbookshelf/catalog/
│  ├─ BookCatalogApplication.java                         # 啟用設定屬性掃描
│  ├─ api/ApiExceptionHandler.java                        # Provider 不可用 → 502
│  └─ provider/
│     ├─ MockBookCatalogProvider.java                     # 非 local Profile 的既有假資料
│     └─ google/
│        ├─ CatalogProviderUnavailableException.java      # 外部服務故障例外
│        ├─ GoogleBooksCatalogProvider.java               # Google HTTP 查詢與 ISBN 比對
│        ├─ GoogleBooksProperties.java                    # app.google-books.api-key
│        └─ GoogleBooksVolumesResponse.java               # Google JSON 的最小模型
└─ src/test/java/com/peng3/personalbookshelf/catalog/
   ├─ api/ApiExceptionHandlerTest.java                    # HTTP 502 契約
   └─ provider/google/GoogleBooksCatalogProviderTest.java # 不連網 HTTP 模擬
~~~

不修改 `src/main/resources/application.yaml`；該檔目前有使用者尚未提交的換行差異。真實 API Key 只放在已由 `.gitignore` 忽略的 `src/main/resources/application-local.yaml`。

### Task 1: 設定屬性與 Provider Profile 切換

**Files:**
- Modify: `src/main/java/com/peng3/personalbookshelf/catalog/BookCatalogApplication.java`
- Modify: `src/main/java/com/peng3/personalbookshelf/catalog/provider/MockBookCatalogProvider.java`
- Create: `src/main/java/com/peng3/personalbookshelf/catalog/provider/google/GoogleBooksProperties.java`
- Test: `src/test/java/com/peng3/personalbookshelf/catalog/api/BookLookupControllerTest.java`

- [ ] **Step 1: 將既有 Mock Controller 測試當作回歸測試。**

既有 `shouldReturnFoundAndNotFoundBooks()` 已驗證未啟用 `local` 時，ISBN `9786264141802` 回傳 `found=true`。保持測試內容不變。

- [ ] **Step 2: 先執行測試取得綠色基準。**

Run: `./mvnw.cmd test -Dtest=BookLookupControllerTest`

Expected: `Tests run: 3, Failures: 0, Errors: 0`，且 `BUILD SUCCESS`。

- [ ] **Step 3: 實作最小設定與 Profile 切換。**

Create `GoogleBooksProperties.java`:

~~~java
package com.peng3.personalbookshelf.catalog.provider.google;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.google-books")
public record GoogleBooksProperties(String apiKey) {
}
~~~

Modify `BookCatalogApplication.java`:

~~~java
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class BookCatalogApplication {
    // main 不變
}
~~~

Modify `MockBookCatalogProvider.java`:

~~~java
import org.springframework.context.annotation.Profile;

@Component
@Profile("!local")
public class MockBookCatalogProvider implements BookCatalogProvider {
    // 既有內容不變
}
~~~

- [ ] **Step 4: 再執行 Mock 回歸測試。**

Run: `./mvnw.cmd test -Dtest=BookLookupControllerTest`

Expected: `BUILD SUCCESS`；未設定 `local` 時不需 API Key，也不會建立 Google Provider。

- [ ] **Step 5: 提交獨立的設定變更。**

~~~bash
git add src/main/java/com/peng3/personalbookshelf/catalog/BookCatalogApplication.java src/main/java/com/peng3/personalbookshelf/catalog/provider/MockBookCatalogProvider.java src/main/java/com/peng3/personalbookshelf/catalog/provider/google/GoogleBooksProperties.java
git commit -m "feat: add profile-based catalog provider configuration"
~~~

### Task 2: 以測試定義 Google 回應的精確 ISBN 映射

**Files:**
- Create: `src/main/java/com/peng3/personalbookshelf/catalog/provider/google/GoogleBooksVolumesResponse.java`
- Create: `src/main/java/com/peng3/personalbookshelf/catalog/provider/google/GoogleBooksCatalogProvider.java`
- Test: `src/test/java/com/peng3/personalbookshelf/catalog/provider/google/GoogleBooksCatalogProviderTest.java`

- [ ] **Step 1: 寫出精確 `ISBN_13` 命中的失敗測試。**

Create `GoogleBooksCatalogProviderTest.java`:

~~~java
package com.peng3.personalbookshelf.catalog.provider.google;

import com.peng3.personalbookshelf.catalog.domain.Book;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GoogleBooksCatalogProviderTest {

    private MockRestServiceServer server;
    private GoogleBooksCatalogProvider provider;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        provider = new GoogleBooksCatalogProvider(builder, new GoogleBooksProperties("test-key"));
    }

    @Test
    void shouldMapBookOnlyWhenGoogleReturnsExactIsbn13() {
        server.expect(requestTo(startsWith("https://www.googleapis.com/books/v1/volumes?")))
                .andExpect(method(GET))
                .andExpect(queryParam("q", "isbn:9786264141802"))
                .andExpect(queryParam("printType", "books"))
                .andExpect(queryParam("maxResults", "10"))
                .andExpect(queryParam("key", "test-key"))
                .andRespond(withSuccess("""
                        {
                          "items": [{
                            "volumeInfo": {
                              "title": "Android 開發實戰",
                              "authors": ["王小明"],
                              "publisher": "測試出版社",
                              "publishedDate": "2025-01-01",
                              "description": "測試簡介",
                              "industryIdentifiers": [
                                {"type": "ISBN_13", "identifier": "9786264141802"}
                              ]
                            }
                          }]
                        }
                        """, MediaType.APPLICATION_JSON));

        Optional<Book> result = provider.findByIsbn("9786264141802");

        assertThat(result).hasValueSatisfying(book -> {
            assertThat(book.isbn()).isEqualTo("9786264141802");
            assertThat(book.title()).isEqualTo("Android 開發實戰");
            assertThat(book.authors()).containsExactly("王小明");
            assertThat(book.publisher()).isEqualTo("測試出版社");
            assertThat(book.publishedDate()).isEqualTo("2025-01-01");
            assertThat(book.description()).isEqualTo("測試簡介");
        });
        server.verify();
    }
}
~~~

- [ ] **Step 2: 執行測試，確認尚未實作而失敗。**

Run: `./mvnw.cmd test -Dtest=GoogleBooksCatalogProviderTest`

Expected: 編譯失敗，指出 `GoogleBooksCatalogProvider` 尚不存在。

- [ ] **Step 3: 實作 Google JSON 模型與最小 HTTP Provider。**

Create `GoogleBooksVolumesResponse.java`:

~~~java
package com.peng3.personalbookshelf.catalog.provider.google;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GoogleBooksVolumesResponse(List<Item> items) {

    public List<Item> safeItems() {
        return items == null ? List.of() : items;
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
~~~

Create `GoogleBooksCatalogProvider.java`:

~~~java
package com.peng3.personalbookshelf.catalog.provider.google;

import com.peng3.personalbookshelf.catalog.domain.Book;
import com.peng3.personalbookshelf.catalog.provider.BookCatalogProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Objects;
import java.util.Optional;

@Component
@Profile("local")
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
            throw new IllegalStateException("Google Books API Key 未設定");
        }

        GoogleBooksVolumesResponse response = restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/volumes")
                        .queryParam("q", "isbn:" + isbn)
                        .queryParam("printType", "books")
                        .queryParam("maxResults", 10)
                        .queryParam("key", properties.apiKey())
                        .build())
                .retrieve()
                .body(GoogleBooksVolumesResponse.class);

        return toExactBook(isbn, response);
    }

    private Optional<Book> toExactBook(String isbn, GoogleBooksVolumesResponse response) {
        if (response == null) {
            return Optional.empty();
        }

        return response.safeItems().stream()
                .map(GoogleBooksVolumesResponse.Item::volumeInfo)
                .filter(Objects::nonNull)
                .filter(volumeInfo -> volumeInfo.hasExactIsbn13(isbn))
                .findFirst()
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
~~~

這個版本不加 `country`、`langRestrict`、快取、重試或第二資料來源。

- [ ] **Step 4: 重新執行指定測試。**

Run: `./mvnw.cmd test -Dtest=GoogleBooksCatalogProviderTest#shouldMapBookOnlyWhenGoogleReturnsExactIsbn13`

Expected: `Tests run: 1, Failures: 0, Errors: 0`；`server.verify()` 證實送出四個查詢參數。

- [ ] **Step 5: 提交 Google Provider 基本能力。**

~~~bash
git add src/main/java/com/peng3/personalbookshelf/catalog/provider/google/GoogleBooksVolumesResponse.java src/main/java/com/peng3/personalbookshelf/catalog/provider/google/GoogleBooksCatalogProvider.java src/test/java/com/peng3/personalbookshelf/catalog/provider/google/GoogleBooksCatalogProviderTest.java
git commit -m "feat: add Google Books catalog provider"
~~~

### Task 3: 防止非精確搜尋結果污染書櫃

**Files:**
- Modify: `src/test/java/com/peng3/personalbookshelf/catalog/provider/google/GoogleBooksCatalogProviderTest.java`
- Modify: `src/main/java/com/peng3/personalbookshelf/catalog/provider/google/GoogleBooksVolumesResponse.java`（僅在測試指出需要時）

- [ ] **Step 1: 新增「Google 結果 ISBN 不一致」測試。**

Add to `GoogleBooksCatalogProviderTest.java`:

~~~java
@Test
void shouldReturnEmptyWhenGoogleResultDoesNotContainExactIsbn13() {
    server.expect(requestTo(startsWith("https://www.googleapis.com/books/v1/volumes?")))
            .andRespond(withSuccess("""
                    {
                      "items": [{
                        "volumeInfo": {
                          "title": "相似但不是同一本書",
                          "industryIdentifiers": [
                            {"type": "ISBN_13", "identifier": "9789573293343"}
                          ]
                        }
                      }]
                    }
                    """, MediaType.APPLICATION_JSON));

    Optional<Book> result = provider.findByIsbn("9786264141802");

    assertThat(result).isEmpty();
    server.verify();
}
~~~

- [ ] **Step 2: 執行測試。**

Run: `./mvnw.cmd test -Dtest=GoogleBooksCatalogProviderTest#shouldReturnEmptyWhenGoogleResultDoesNotContainExactIsbn13`

Expected: 若 Task 2 的 `hasExactIsbn13` 已完成則直接通過；否則應先失敗，再補上該篩選。

- [ ] **Step 3: 確認精確比對規則為唯一接受條件。**

~~~java
public boolean hasExactIsbn13(String isbn) {
    return industryIdentifiers != null && industryIdentifiers.stream()
            .anyMatch(identifier -> "ISBN_13".equals(identifier.type())
                    && isbn.equals(identifier.identifier()));
}
~~~

不可僅依搜尋第一筆、書名或 ISBN-10 判定成功。

- [ ] **Step 4: 執行 Provider 所有測試。**

Run: `./mvnw.cmd test -Dtest=GoogleBooksCatalogProviderTest`

Expected: 全部通過，且不應發生任何對 `www.googleapis.com` 的真實網路連線。

- [ ] **Step 5: 提交精確比對保護。**

~~~bash
git add src/main/java/com/peng3/personalbookshelf/catalog/provider/google/GoogleBooksVolumesResponse.java src/test/java/com/peng3/personalbookshelf/catalog/provider/google/GoogleBooksCatalogProviderTest.java
git commit -m "test: cover exact Google ISBN matching"
~~~

### Task 4: 外部服務不可用時不可誤回 found=false

**Files:**
- Create: `src/main/java/com/peng3/personalbookshelf/catalog/provider/google/CatalogProviderUnavailableException.java`
- Modify: `src/main/java/com/peng3/personalbookshelf/catalog/provider/google/GoogleBooksCatalogProvider.java`
- Modify: `src/test/java/com/peng3/personalbookshelf/catalog/provider/google/GoogleBooksCatalogProviderTest.java`

- [ ] **Step 1: 寫出 Google 429 與 500 必須拋出專用例外的失敗測試。**

~~~java
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.springframework.http.HttpStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

@Test
void shouldFailExplicitlyWhenGoogleRateLimitsRequest() {
    server.expect(requestTo(startsWith("https://www.googleapis.com/books/v1/volumes?")))
            .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

    assertThatThrownBy(() -> provider.findByIsbn("9786264141802"))
            .isInstanceOf(CatalogProviderUnavailableException.class);
}
~~~

再以 `HttpStatus.INTERNAL_SERVER_ERROR` 寫一個相同斷言的 500 測試。

- [ ] **Step 2: 執行 429 測試，確認尚未有專用例外而失敗。**

Run: `./mvnw.cmd test -Dtest=GoogleBooksCatalogProviderTest#shouldFailExplicitlyWhenGoogleRateLimitsRequest`

Expected: 編譯失敗或例外型別不符，因 `CatalogProviderUnavailableException` 尚不存在。

- [ ] **Step 3: 實作專用例外並包裝 HTTP 用戶端例外。**

Create `CatalogProviderUnavailableException.java`:

~~~java
package com.peng3.personalbookshelf.catalog.provider.google;

public class CatalogProviderUnavailableException extends RuntimeException {

    public CatalogProviderUnavailableException(String message) {
        super(message);
    }

    public CatalogProviderUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
~~~

將 `GoogleBooksCatalogProvider#findByIsbn` 改為：

~~~java
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
        return toExactBook(isbn, response);
    } catch (RestClientException exception) {
        throw new CatalogProviderUnavailableException("Google Books 服務暫時無法使用", exception);
    }
}
~~~

`RestClientException` 包含 Google 429、5xx 與連線／讀取逾時的 Spring HTTP 用戶端例外。這些情況不得改成 `Optional.empty()`。

- [ ] **Step 4: 執行 Provider 全部測試。**

Run: `./mvnw.cmd test -Dtest=GoogleBooksCatalogProviderTest`

Expected: 429、500、精確命中與非精確結果的測試全數通過。

- [ ] **Step 5: 提交外部失敗模型。**

~~~bash
git add src/main/java/com/peng3/personalbookshelf/catalog/provider/google/CatalogProviderUnavailableException.java src/main/java/com/peng3/personalbookshelf/catalog/provider/google/GoogleBooksCatalogProvider.java src/test/java/com/peng3/personalbookshelf/catalog/provider/google/GoogleBooksCatalogProviderTest.java
git commit -m "feat: report unavailable catalog provider"
~~~

### Task 5: 將 Provider 不可用轉為 HTTP 502

**Files:**
- Create: `src/main/java/com/peng3/personalbookshelf/catalog/api/ApiExceptionHandler.java`
- Create: `src/test/java/com/peng3/personalbookshelf/catalog/api/ApiExceptionHandlerTest.java`

- [ ] **Step 1: 寫出 API 回應 502 的失敗測試。**

Create `ApiExceptionHandlerTest.java`:

~~~java
package com.peng3.personalbookshelf.catalog.api;

import com.peng3.personalbookshelf.catalog.provider.BookCatalogProvider;
import com.peng3.personalbookshelf.catalog.provider.google.CatalogProviderUnavailableException;
import com.peng3.personalbookshelf.catalog.service.BookLookupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ApiExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        BookCatalogProvider unavailableProvider = isbn -> {
            throw new CatalogProviderUnavailableException("Google Books 服務暫時無法使用");
        };

        mockMvc = MockMvcBuilders.standaloneSetup(
                        new BookLookupController(new BookLookupService(unavailableProvider)))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void shouldReturn502WhenCatalogProviderIsUnavailable() throws Exception {
        mockMvc.perform(post("/api/v1/books/lookup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{ "isbns": ["9786264141802"] }"""))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.status").value(502))
                .andExpect(jsonPath("$.title").value("書目服務暫時無法使用"));
    }
}
~~~

- [ ] **Step 2: 執行測試，確認缺少 Controller Advice 而失敗。**

Run: `./mvnw.cmd test -Dtest=ApiExceptionHandlerTest`

Expected: 編譯失敗，指出 `ApiExceptionHandler` 尚不存在。

- [ ] **Step 3: 實作最小全域例外處理器。**

Create `ApiExceptionHandler.java`:

~~~java
package com.peng3.personalbookshelf.catalog.api;

import com.peng3.personalbookshelf.catalog.provider.google.CatalogProviderUnavailableException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(CatalogProviderUnavailableException.class)
    public ProblemDetail handleCatalogProviderUnavailable(
            CatalogProviderUnavailableException exception
    ) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_GATEWAY,
                "外部書目服務暫時無法使用，請稍後再試。"
        );
        problem.setTitle("書目服務暫時無法使用");
        return problem;
    }
}
~~~

- [ ] **Step 4: 執行 Web 例外處理測試。**

Run: `./mvnw.cmd test -Dtest=ApiExceptionHandlerTest`

Expected: `Tests run: 1, Failures: 0, Errors: 0`，Problem Details JSON 的 `status` 為 502。

- [ ] **Step 5: 提交 HTTP 錯誤契約。**

~~~bash
git add src/main/java/com/peng3/personalbookshelf/catalog/api/ApiExceptionHandler.java src/test/java/com/peng3/personalbookshelf/catalog/api/ApiExceptionHandlerTest.java
git commit -m "feat: return 502 for unavailable catalog provider"
~~~

### Task 6: 全量驗證與本機手動驗收

**Files:**
- Create locally but **do not commit**: `src/main/resources/application-local.yaml`
- Verify: `src/main/resources/application.yaml`（不得放入 API Key）

- [ ] **Step 1: 執行完整自動化測試。**

Run: `./mvnw.cmd test`

Expected: `BUILD SUCCESS`；測試不需 Google API Key，亦不呼叫 Google 網路服務。

- [ ] **Step 2: 建立本機私密設定檔。**

在 `src/main/resources/application-local.yaml` 建立內容，將 `YOUR_KEY` 替換為自己的金鑰；禁止把真實 key 貼入終端輸出、對話或 Git：

~~~yaml
app:
  google-books:
    api-key: "YOUR_KEY"
~~~

執行 `git status --short`，預期此檔不出現；若已被追蹤，先停止並移除追蹤，不可提交它。

- [ ] **Step 3: 使用 local Profile 啟動並驗收。**

在 IntelliJ Run Configuration 加入環境變數 `SPRING_PROFILES_ACTIVE=local`，或執行：

~~~powershell
$env:SPRING_PROFILES_ACTIVE = "local"
.\mvnw.cmd spring-boot:run
~~~

另開 PowerShell 視窗：

~~~powershell
$body = @{ isbns = @("9786264141802") } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/v1/books/lookup" -ContentType "application/json" -Body $body
~~~

Expected: 精確 ISBN_13 有資料時 `found=true`；查無資料時 `found=false`；Google 受限或不可用時回傳 HTTP 502，而不是 `found=false`。

- [ ] **Step 4: 檢查提交範圍。**

Run: `git status --short`

Expected: 不包含 `application-local.yaml`、`target/`、IDE 暫存檔或使用者既有 `application.yaml` 的換行差異。前面已採分段 commit 時，此步僅確認工作樹乾淨，不建立重複 commit。
