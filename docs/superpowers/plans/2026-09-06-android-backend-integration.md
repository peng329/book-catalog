# Android Backend Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 讓 Android App 在完成掃描後自動批次查詢本機 Spring Boot，顯示查詢結果，並將完整書目資料追加至新版 CSV。

**Architecture:** Retrofit 負責 REST 契約與 JSON 轉換，Repository 隔離網路錯誤與補齊遺漏 ISBN，Compose 只控制畫面狀態。CSV 格式化維持為獨立純函式，以單元測試驗證跳脫規則；實機使用 `adb reverse` 連到電腦的 8080 埠。

**Tech Stack:** Kotlin、Jetpack Compose、Retrofit 3.0.0、Gson converter、Apache Commons CSV 1.14.1、Kotlin coroutines、JUnit 4、Android Gradle Plugin 9.3.1

---

## 檔案結構

- Create: `app/src/main/java/com/yourname/personalbookshelf/catalog/BookCatalogModels.kt`：遠端 DTO 與查詢結果。
- Create: `app/src/main/java/com/yourname/personalbookshelf/catalog/BookCatalogApi.kt`：Retrofit API 契約。
- Create: `app/src/main/java/com/yourname/personalbookshelf/catalog/BookCatalogClient.kt`：以 `BuildConfig.API_BASE_URL` 建立 Retrofit。
- Create: `app/src/main/java/com/yourname/personalbookshelf/catalog/BookCatalogRepository.kt`：批次查詢、錯誤降級與 ISBN 補齊。
- Create: `app/src/main/java/com/yourname/personalbookshelf/catalog/BookCsvFormatter.kt`：CSV v2 欄位與跳脫。
- Create: `app/src/main/java/com/yourname/personalbookshelf/catalog/BookCsvReader.kt`：從選定的 CSV 讀取已收藏 ISBN。
- Modify: `app/src/main/java/com/yourname/personalbookshelf/MainActivity.kt`：自動查詢、結果顯示與 v2 CSV 儲存。
- Modify: `app/build.gradle.kts`：加入 Retrofit 與測試相依套件。
- Test: `app/src/test/java/com/yourname/personalbookshelf/catalog/BookCatalogRepositoryTest.kt`。
- Test: `app/src/test/java/com/yourname/personalbookshelf/catalog/BookCsvFormatterTest.kt`。
- Test: `app/src/test/java/com/yourname/personalbookshelf/catalog/BookCsvReaderTest.kt`。

### Task 1: Retrofit API 契約與用戶端

- [ ] 在 `app/build.gradle.kts` 加入 `retrofit:3.0.0` 與 `converter-gson:3.0.0`。
- [ ] 建立請求與回應 DTO，欄位與後端 `BookLookupRequest`、`BookLookupResponse`、`BookResponse` 完全一致。
- [ ] 建立 `BookCatalogApi.lookup()`，使用 `POST api/v1/books/lookup` 與 `suspend` 回傳。
- [ ] 建立 `BookCatalogClient`，Base URL 僅取自 `BuildConfig.API_BASE_URL`。
- [ ] 執行 `gradlew.bat testDebugUnitTest`，確認編譯成功。

### Task 2: Repository 成功與失敗資料流

- [ ] 先建立失敗測試：成功回應維持請求順序，後端遺漏 ISBN 時補成 `found=false`。
- [ ] 執行測試，確認因 Repository 尚未實作而失敗。
- [ ] 實作最小 Repository 讓測試通過。
- [ ] 再建立失敗測試：API 丟出例外時，保留全部 ISBN 並回傳失敗結果。
- [ ] 執行測試，確認先失敗，再完成錯誤降級實作。
- [ ] 執行全部 Repository 測試。

### Task 3: CSV v2 格式化

- [ ] 先建立失敗測試，驗證欄位順序為 `isbn,title,authors,publisher,publishedDate,description,found`。
- [ ] 建立失敗測試，驗證逗號、雙引號、換行與空值均可安全輸出。
- [ ] 實作 `BookCsvFormatter`，所有文字欄位使用 CSV 雙引號規則，多位作者以 `；` 合併。
- [ ] 新檔輸出 UTF-8 BOM 與標題；追加資料時不重寫 BOM 或標題。
- [ ] 執行 CSV 與全部單元測試。

### Task 4: Compose 自動查詢流程

- [ ] 在 `MainActivity.kt` 建立 Idle、Loading、Ready 與 Failed 畫面狀態。
- [ ] 按下「完成本次掃描」後複製 ISBN 清單並啟動 coroutine 查詢。
- [ ] Loading 時顯示「正在查詢書籍資料」並禁止儲存與重複完成。
- [ ] Ready 時顯示每筆 ISBN 與書名；查無資料顯示「查無資料」。
- [ ] Failed 時顯示「查詢失敗，但可保留 ISBN」，並保留可儲存的空白書目資料。
- [ ] 將原本只寫 ISBN 的函式改為寫入 Repository 產生的書目清單。

### Task 5: CSV v2 檔案與驗證

- [ ] 改用新的偏好設定鍵保存 v2 CSV URI，不讀取舊版 `csv_uri`。
- [ ] 加入「建立新 CSV」與「選擇既有 CSV」，保存使用者選定的 URI，而非依賴固定檔名。
- [ ] 先建立 CSV 讀取失敗測試，涵蓋 UTF-8 BOM、欄位內換行、重複 ISBN 與錯誤標題。
- [ ] 使用 Apache Commons CSV 實作 ISBN 載入，執行測試確認通過。
- [ ] 掃描或手動輸入時同時檢查既有 CSV 與本次清單；重複時略過並提示。
- [ ] 第一次儲存預設檔名使用 `personal_bookshelf_v2.csv`，不覆蓋舊 CSV。
- [ ] 執行 `gradlew.bat testDebugUnitTest assembleDebug`，要求所有測試與 Debug 建置成功。
- [ ] 啟動 Spring Boot，確認電腦端 curl 成功。
- [ ] 執行 `adb reverse tcp:8080 tcp:8080`，重新安裝 Debug App。
- [ ] 實機驗證：掃描、完成、自動查詢、顯示書名、建立 v2 CSV。
- [ ] 再執行一個掃描工作階段，確認追加資料且未覆蓋第一批內容。

## 邊界

- 本階段不加入依賴注入框架、資料庫、登入、雲端部署或重試機制。
- Android 專案目前沒有 Git Repository，因此 Android 異動不建立提交；後續若要版控需另行初始化或納入主專案。
