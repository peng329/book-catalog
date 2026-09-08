# Android 與本機書目後端串接設計

## 目標

Android App 在使用者按下「完成本次掃描」後，自動將本次 ISBN 清單批次送至本機 Spring Boot API，取得書名、作者、出版社、出版日期與簡介，並讓使用者將結果寫入新版 CSV。

## 開發環境連線

- Spring Boot 執行於電腦 `127.0.0.1:8080`。
- Android 實機透過 USB 連接並執行 `adb reverse tcp:8080 tcp:8080`。
- Debug App 使用 `http://127.0.0.1:8080/` 作為 API Base URL。
- 僅 Debug Manifest 允許明文 HTTP；正式版本不開放。

## Android 元件邊界

- `BookCatalogApi`：只描述 REST API 契約。
- 遠端 DTO：對應後端的查詢請求及回應 JSON。
- `BookCatalogRepository`：執行批次查詢、補齊缺少的 ISBN，並把網路例外轉為 UI 可處理的結果。
- Compose 畫面：管理掃描工作階段、查詢中、查詢成功及查詢失敗等畫面狀態，不直接處理 HTTP 細節。
- CSV 寫入器：負責欄位順序與 CSV 跳脫，不依賴 Retrofit。

## 操作與資料流

1. 使用者掃描或手動輸入 ISBN，加入本次清單。
2. 使用者按下「完成本次掃描」。
3. App 顯示「正在查詢書籍資料」，並停用重複操作。
4. App 將最多 50 筆 ISBN 以單一 POST 請求送至 `/api/v1/books/lookup`。
5. 查詢成功時，App 顯示各 ISBN 的書名或「查無資料」，並允許儲存。
6. 查詢失敗時，App 顯示「查詢失敗，但可保留 ISBN」，以只有 ISBN、其他欄位留空的資料允許儲存。

## CSV v2

- 不修改或覆蓋既有的單欄位 CSV。
- App 提供「建立新 CSV」與「選擇既有 CSV」入口，實際檔名與資料夾由使用者決定。
- `personal_bookshelf_v2.csv` 只作為建立新檔時的建議檔名，不用檔名尋找書櫃。
- App 保存 Android Storage Access Framework 回傳的 URI 與持久讀寫權限，後續以同一 URI 讀取及追加。
- 欄位順序：`isbn,title,authors,publisher,publishedDate,description,found`。
- 多位作者以可讀分隔符號合併為同一欄。
- 所有文字欄位依 CSV 規則處理逗號、雙引號與換行。
- 後續工作階段沿用使用者授權的 v2 檔案並追加資料，不覆蓋舊內容。

## 重複 ISBN

- 選擇或重新開啟 CSV 時，使用可靠的 CSV 解析器讀取 `isbn` 欄位，載入已收藏集合。
- 掃描或手動輸入時，先檢查已收藏集合與本次清單。
- 已存在於 CSV：不加入本次清單、不呼叫後端，顯示「此 ISBN 已收藏」。
- 已存在於本次清單：不重複加入，顯示「此 ISBN 已在本次清單」。
- 成功追加 CSV 後，立即把本次 ISBN 合併至已收藏集合。
- CSV 無法讀取、格式不符或授權失效時，不猜測內容，提示使用者重新選擇檔案。

## 錯誤處理

- HTTP、逾時或 JSON 解析失敗：保留全部 ISBN，其他欄位留空。
- 後端對單一本書回傳 `found=false`：保留該 ISBN，其他欄位依回應留空。
- 回應遺漏某個 ISBN：Repository 補上一筆 `found=false`，避免資料消失。
- CSV 寫入失敗：保留目前查詢結果並顯示錯誤，允許使用者再次儲存。

## 驗證

- Repository 單元測試：成功、整批失敗及回應缺漏 ISBN。
- CSV 單元測試：標題、逗號、雙引號、換行及空值。
- CSV 讀取測試：UTF-8 BOM、含換行的簡介、重複 ISBN 與錯誤標題。
- Android Debug 建置。
- 實機端對端測試：掃描 ISBN、完成、自動查詢、顯示結果、建立 v2 CSV、第二次追加且不覆蓋舊資料。
