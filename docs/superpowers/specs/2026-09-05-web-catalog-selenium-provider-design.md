# Web Catalog Selenium Provider Design

## 目標

在 `local` Profile 中加入可設定的網頁書目來源，優先取得中文書的書名、作者、出版社、出版日期與內容簡介；查無資料或來源暫時不可用時，使用既有 Google Books Provider 備援。

## 範圍

第一版包含：

- Selenium 控制 Chrome 載入書籍頁面。
- Jsoup 解析載入完成的 HTML。
- 可替換的頁面載入介面。
- 網頁書目來源優先、Google Books 備援。
- 查詢序列化與固定請求間隔。
- HTML 解析、Provider 轉換、備援流程及手動 live test。

第一版不包含 Playwright、持久化快取、驗證碼或存取限制規避、瀏覽器池、複雜重試。

## 架構

```text
BookLookupService
    ↓
FallbackBookCatalogProvider (@Primary, local)
    ├─ WebCatalogProvider
    │   ├─ WebCatalogPageLoader
    │   │   └─ SeleniumWebCatalogPageLoader
    │   └─ WebCatalogHtmlParser
    └─ GoogleBooksCatalogProvider
```

`WebCatalogPageLoader` 只負責根據 ISBN 取得 HTML，隔離瀏覽器實作；`WebCatalogHtmlParser` 只處理 HTML 到 `Book` 的轉換；`WebCatalogProvider` 組合兩者，不知道備援來源。

`SeleniumWebCatalogPageLoader` 延遲建立並重用單一 WebDriver，序列化 `load`，套用請求間隔與頁面逾時，並在 Spring 關閉時釋放瀏覽器。

## 資料流

```text
ISBN
 → Selenium page load
 → Jsoup parse
 → found: Book
 → not found / expected provider failure: Google Books
 → still not found: found=false
```

來源失敗只觸發單筆備援，不改變整批 ISBN 的既有處理語意。

## 設定

```yaml
catalog:
  web-source:
    base-url: <required local value>
    headless: true
    request-delay: 2s
    page-timeout: 20s
```

Base URL 沒有預設值，必須由環境提供；其他欄位提供安全的操作預設值。

## 錯誤處理

瀏覽器導航失敗或頁面載入逾時時，轉為 `WebCatalogProviderUnavailableException`，再由 fallback 改查 Google Books。HTML 無書名時回傳空結果。所有訊息僅使用「網頁書目來源」，不記錄來源網域或完整 URL。

## 測試策略

- 固定的精簡 HTML fixture 驗證欄位解析與缺少書名。
- 驗證網頁書目成功、空結果、來源不可用三種備援分支。
- 驗證 WebDriver 導航、例外轉換與關閉。
- live test 由 `web-catalog.live-test` 手動開啟，Base URL 僅讀取 `catalog.web-source.base-url`；未設定時跳過。

## 驗收標準

- 網頁書目結果成功時不呼叫 Google Books。
- 空結果或明確來源不可用時呼叫 Google Books。
- WebDriver 不在 Spring 啟動時建立。
- 更換頁面載入技術時，不需修改解析器與 fallback。
- 自動測試全數通過，live test 預設跳過。
