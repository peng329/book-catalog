# Google Books ISBN 搜尋結果回退設計

## 背景

Google Books 對 `q=isbn:<ISBN-13>` 已能找到部分臺灣書目，但回傳的 `volumeInfo.industryIdentifiers` 可能只包含 OCLC 識別碼，沒有 `ISBN_13`。現行程式僅接受明列且完全相符的 `ISBN_13`，因而錯誤回傳查無資料。

## 決策

Google Books Provider 維持 `q=isbn:<ISBN-13>` 查詢，不加入 `country` 或 `langRestrict`。實測顯示這些條件對目標 ISBN 沒有改變結果。

書目選擇規則依序如下：

1. 回應項目含有完全相符的 `ISBN_13` 時，採用該項目。
2. 沒有相符 ISBN 時，僅在 Google 回傳 `totalItems = 1`，且目前頁面也只有一筆可用書目時，採用該唯一結果。
3. 多筆結果、空結果或缺少可用書目時，維持查無資料。

第二條只適用於原本已由 Google 的 `isbn:` 欄位搜尋命中的結果；它不會用書名、作者或不同版本 ISBN 進行模糊比對。

## 程式調整

- `GoogleBooksVolumesResponse` 新增 `totalItems` 對應欄位。
- `GoogleBooksCatalogProvider` 將「選擇書目」與「轉換為 `Book`」分開，實作上述優先順序。
- HTTP 呼叫、API Key 管理與外部服務失敗時的 502 行為不變。

## 驗收與測試

- 明列相符 `ISBN_13` 的結果可成功映射。
- `totalItems = 1`、僅有 OCLC 識別碼的結果可成功映射。
- `totalItems > 1` 且無相符 `ISBN_13` 時維持查無資料。
- Google 429 與 5xx 仍轉為 `CatalogProviderUnavailableException`。
