# Google Books Catalog Provider 設計

## 目標

讓電子書櫃後端可依 ISBN 呼叫 Google Books API 取得真實書目資料，同時維持既有 API 契約與 Mock Provider 測試能力。

## Provider 選擇

- 預設與測試環境使用 `MockBookCatalogProvider`。
- 啟用 `local` Spring Profile 時使用 `GoogleBooksCatalogProvider`。
- Google API Key 僅放在未提交的 `src/main/resources/application-local.yaml`。
- Key 設定名稱為 `app.google-books.api-key`；不得放入 Android App、正式設定檔或 Git。

## Google 查詢規則

- 呼叫 `GET https://www.googleapis.com/books/v1/volumes`。
- 查詢參數：`q=isbn:{isbn}`、`printType=books`、`maxResults=10` 與 `key`。
- 不套用語言或國別篩選，以保留少量英文書的查詢能力。
- 僅接受 `industryIdentifiers` 中 `type=ISBN_13` 且 identifier 與輸入 ISBN 完全一致的項目。
- 映射欄位：ISBN、title、authors、publisher、publishedDate、description。
- 缺少的可選書目欄位保留空值；沒有精確 ISBN 命中時回傳 `Optional.empty()`。

## 失敗處理

- 網路逾時、Google API `429` 或 `5xx` 視為外部書目服務不可用。
- 服務不可用時拋出專用例外，並以 `502 Bad Gateway` 回應呼叫端。
- 不可把服務不可用誤判為 `found=false`。

## 測試

- 保留現有 Mock Provider 的 Controller 整合測試。
- 新增 Google Provider 的 HTTP 模擬測試，驗證精確 ISBN 映射、查無精確匹配與外部服務失敗。
- 測試不得使用真實 API Key 或呼叫 Google 網路服務。

## 非目標

- 不加入快取、重試、限流、第二資料來源或雲端部署。
