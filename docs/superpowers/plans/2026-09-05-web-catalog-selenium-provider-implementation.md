# Web Catalog Selenium Provider Implementation Plan

**Goal:** 在 `local` Profile 中優先使用可設定的網頁書目來源，失敗時使用既有 Google Books Provider。

**Architecture:** 保留 `BookCatalogProvider` 邊界；`FallbackBookCatalogProvider` 為 `@Primary`，依序呼叫 `WebCatalogProvider` 與 Google Books。網頁來源拆成頁面載入、HTML 解析與 Provider 三層。

**Tech Stack:** Java 17、Spring Boot、Selenium、Jsoup、JUnit 5、Mockito、AssertJ。

## 檔案結構

```text
src/main/java/com/peng3/personalbookshelf/catalog/provider/
├─ FallbackBookCatalogProvider.java
└─ webcatalog/
   ├─ WebCatalogProvider.java
   ├─ WebCatalogHtmlParser.java
   ├─ WebCatalogPageLoader.java
   ├─ WebCatalogProperties.java
   ├─ WebCatalogProviderUnavailableException.java
   └─ selenium/
      ├─ WebCatalogWebDriverFactory.java
      ├─ ChromeWebCatalogWebDriverFactory.java
      └─ SeleniumWebCatalogPageLoader.java

src/test/java/com/peng3/personalbookshelf/catalog/provider/webcatalog/
├─ WebCatalogProviderTest.java
├─ WebCatalogHtmlParserTest.java
├─ WebCatalogPropertiesTest.java
└─ selenium/
   ├─ SeleniumWebCatalogPageLoaderTest.java
   └─ WebCatalogSeleniumLiveTest.java

src/test/resources/webcatalog/sample-book-page.html
```

## Task 1: 頁面載入層

1. 先寫 Selenium loader 測試，驗證導航、HTML 回傳、例外轉換與關閉。
2. 建立 `WebCatalogPageLoader`、`WebCatalogWebDriverFactory` 與 properties。
3. 實作延遲建立 WebDriver、頁面逾時、請求間隔與生命週期關閉。
4. 只對瀏覽器失敗拋出 `WebCatalogProviderUnavailableException`。
5. 執行對應測試取得 RED，再以最小實作取得 GREEN。

`WebCatalogProperties` 使用 `@ConfigurationProperties(prefix = "catalog.web-source")`：

```text
baseUrl=<required>
headless=true
requestDelay=2s
pageTimeout=20s
```

## Task 2: HTML 解析層

1. 建立精簡的 `webcatalog/sample-book-page.html` fixture。
2. 先寫 `WebCatalogHtmlParserTest`，涵蓋完整欄位、缺少書名、格式不完整的 JSON-LD 與長簡介。
3. 實作 Jsoup selector 與容錯 JSON-LD 解析。
4. 執行測試確認 RED／GREEN。

解析器只處理字串，不執行網路存取；作者與譯者第一版保留為單一字串。

## Task 3: Provider 與 fallback

1. 先寫 `WebCatalogProviderTest`，驗證 loader 結果會交給 parser。
2. 實作 `WebCatalogProvider`。
3. 更新 fallback 測試，涵蓋成功、空結果、來源不可用。
4. 在 `FallbackBookCatalogProvider` 使用 `webCatalogProvider` 與 `googleBooksCatalogProvider` Qualifier。
5. 只捕捉 `WebCatalogProviderUnavailableException`，避免隱藏其他程式錯誤。

## Task 4: Spring 與 live test

1. Profile 測試確認 local 下 fallback 是主要 Provider，且啟動 Context 不會建立 Chrome。
2. live test 只在 `web-catalog.live-test=true` 時啟用。
3. Base URL 僅從 system property `catalog.web-source.base-url` 取得；缺少時使用 JUnit assumption 跳過。
4. 擷取 HTML 僅寫入 `target/web-catalog-sample.html`。
5. 一般測試不得啟動瀏覽器或對外連線。

## Task 5: 驗證

1. 執行完整 Maven 測試，確認 Failures 與 Errors 為零，live test 為 skipped。
2. 用 marker 檔掃描 tracked、untracked 且非 ignored 的現況內容與路徑，確認零命中。
3. 確認被取代的舊程式、測試、fixture 與文件路徑不存在。
4. 執行 `git diff --check`，確認 diff 只涵蓋 `src/main`、`src/test`、`docs`。
5. 由整合者統一 stage、review 與 commit。
