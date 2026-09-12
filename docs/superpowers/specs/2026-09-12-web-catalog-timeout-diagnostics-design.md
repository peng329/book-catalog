# Web Catalog Timeout Diagnostics Design

## 目的

Render 上的 Selenium 查詢會拋出 `TimeoutException`，但現有安全日誌無法區分頁面仍在載入、已載入阻擋頁，或目標元素不存在。加入一次性的結構診斷，且不得洩漏受保護來源的名稱、URL 或頁面內容。

## 方案比較

1. 在 `SeleniumWebCatalogPageLoader` 捕捉載入失敗時直接記錄診斷：改動最小，且仍可在 driver 關閉前取得狀態，採用。
2. 將診斷資料加入例外並交由 fallback 記錄：分層較完整，但會擴大例外介面與測試範圍，不採用。
3. 新增診斷 API：增加公開攻擊面與維護成本，不符合個人 MVP，不採用。

## 診斷內容

使用單次同步 JavaScript 只回傳以下結構值：

- `document.readyState`
- HTML 字元數
- 目標書名元素是否存在

日誌不得包含 URL、網域、頁面標題、HTML、頁面文字或 Selenium 例外訊息。若診斷本身失敗，只記錄失敗類型，並保留原始載入例外供既有 fallback 流程處理。

## 行為邊界

- 不延長載入逾時。
- 不新增重試。
- 不改變 Google Books 備援流程。
- 每次查詢仍在 `finally` 關閉 ChromeDriver。
- 不由自動測試呼叫 Render 或受保護網頁來源。

## 驗證

新增單元測試，驗證逾時時會輸出三項安全結構值；另驗證診斷失敗不會取代原始 `TimeoutException`，且不會輸出診斷例外訊息。最後執行完整 Maven 測試與既有公開資訊掃描。
