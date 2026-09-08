# 個人電子書櫃 Render 部署設計

## 1. 目標

將 `book-catalog-api` 部署為可由 Android App 在外部網路呼叫的 HTTPS 後端。第一版採 Render Free Web Service，接受服務休眠後首次查詢約一至兩分鐘的等待時間，不部署 Android App，也不新增資料庫。

## 2. 已確認範圍

- Render 只部署 Java 17、Spring Boot 後端。
- Android App 保留在手機，只將後端 Base URL 改為 Render 提供的 `onrender.com` HTTPS 網址。
- CSV 仍由 Android App 儲存在手機，不寫入 Render 檔案系統。
- Google Books API Key 不進入 GitHub、Docker image 或 Android App。
- 受保護網頁書目來源的名稱、網域與 Base URL 不進入 GitHub、Docker image 或 Android App。
- 本機測試仍可使用不版控的 `application-local.yaml`。
- Render 休眠與冷啟動可接受，不使用定時請求規避休眠。

## 3. 整體架構

```text
Android App
    │ HTTPS POST /api/v1/books/lookup
    ▼
Render Free Web Service
    │
    ├─ Web Catalog Provider
    │    └─ Selenium + headless Chromium → 受保護網頁書目來源
    │
    └─ Google Books Provider（備援）
         └─ Google Books API

查詢結果 → Android App → 手機本機 CSV
```

Render 後端保持無狀態，因此 Render 的暫存檔案系統不會造成書櫃資料遺失。

## 4. GitHub 與 Render

1. 後端建立 GitHub Repository 並設定 Git remote。
2. Render Web Service 連接該 GitHub Repository。
3. Render 依 Repository 根目錄的 `Dockerfile` 建置後端。
4. GitHub 後續推送至指定分支時，由 Render 重新建置及部署。
5. Render 提供公開 HTTPS 網址，無須自購 Domain。

Repository 可以公開，但所有敏感值及不希望公開的目標網址都只能存在本機忽略檔案或 Render 環境變數。

目前本機 Git 的既有程式、測試、文件、類別名稱、套件名稱與歷史 commit 已能辨識受保護來源。因後端尚未設定遠端，第一次推送前必須完成遠端歷史淨化：先建立只存在本機且不推送的 Git bundle 備份，再以完成通用化命名的目前工作樹建立單一乾淨 root commit，作為後續本機與遠端的正式主分支。任何舊分支、Tag、commit 與備份均不得推送。此步驟涉及重建正式分支歷史，執行前需另行取得使用者確認。

## 5. Docker 設計

Render 目前沒有 Java/JVM 原生 Runtime，後端使用 Docker 部署。Docker image 必須包含：

- Java 17 Runtime。
- 已建置完成的 Spring Boot JAR。
- Selenium 執行所需的 Chromium／Chrome 與系統函式庫。
- 適合容器環境的 headless Chrome 參數。

採 multi-stage Docker build：第一階段使用 Maven/JDK 編譯並執行測試；第二階段只保留 Runtime、Spring Boot JAR 與瀏覽器，降低最終 image 大小。

不得在 Dockerfile 的 `ARG`、`ENV` 或建置指令中寫入 Google API Key，或受保護來源的名稱、網域及 URL，避免值被保存於 image layer。

Repository 根目錄必須新增 `.dockerignore`，至少排除：

```text
.git
.env
.env.*
.protected-source-markers
src/main/resources/application-local.yml
src/main/resources/application-local.yaml
src/main/resources/application-secret.yml
src/main/resources/application-secret.yaml
target
```

`.gitignore` 只能阻止 Git 提交，不能自動阻止 Docker 將未追蹤的本機檔案送入 build context；因此 `.dockerignore` 是必要的第二道保護。

Chromium 與 ChromeDriver 必須來自同一個 Linux 套件來源並在 image 建置時固定相容版本，不依賴服務啟動後由 Selenium Manager 臨時下載。Docker build 需輸出兩者版本並執行最小 headless 啟動檢查。

## 6. Spring Profile 設計

目前真實 Provider 全部限定於 `local` Profile，而 Mock Provider 在非 `local` Profile 啟用。若原樣部署，Render 會載入 Mock Provider。

調整後的完整 Bean 矩陣如下，表內元件必須同一次修改，不能只改部分 Provider：

| 元件 | `local` | `real` | 未指定 `local`／`real` |
|---|---:|---:|---:|
| `MockBookCatalogProvider` | 關閉 | 關閉 | 啟用 |
| `FallbackBookCatalogProvider` | 啟用 | 啟用 | 關閉 |
| `GoogleBooksCatalogProvider` | 啟用 | 啟用 | 關閉 |
| `WebCatalogProvider` | 啟用 | 啟用 | 關閉 |
| `SeleniumWebCatalogPageLoader` | 啟用 | 啟用 | 關閉 |
| `ChromeWebCatalogWebDriverFactory` | 啟用 | 啟用 | 關閉 |

真實元件使用 `local` 或 `real` 的 Profile 條件；Mock 使用「非 `local` 且非 `real`」條件。Render 設定：

```text
SPRING_PROFILES_ACTIVE=real
```

不新增所謂「雲端 Provider」；既有網頁書目 Provider 改為通用名稱，Google Books 與 Fallback Provider 沿用現有職責，並一起調整啟用條件。

## 7. 設定與祕密管理

### 本機

以下內容放在已被 `.gitignore` 排除的 `application-local.yaml`：

```yaml
app:
  google-books:
    api-key: <本機 Google Books API Key>

catalog:
  web-source:
    base-url: <受保護來源 Base URL>
```

### Render

在 Render Dashboard 的 Environment 設定：

```text
SPRING_PROFILES_ACTIVE=real
APP_GOOGLE_BOOKS_API_KEY=<Google Books API Key>
CATALOG_WEB_SOURCE_BASE_URL=<受保護來源 Base URL>
```

程式需將既有網頁書目來源相關類別、套件、測試資源及訊息改為 `WebCatalog`／`web-source` 等通用名稱，並移除公開的預設 Base URL；缺少 Google Key 或受保護來源 URL 時，真實 Profile 應在啟動階段明確失敗，不應靜默改用公開預設值。

日誌與 API 回應不得輸出 API Key、受保護來源的名稱、網域、Base URL，或含 Key 的完整 Google Books 請求網址。

### 通用化重新命名矩陣

以下是唯一允許的目標名稱，實作計畫不得自行發明其他命名：

| 目前職責 | 通用化後名稱／路徑 |
|---|---|
| 網頁來源 Provider 套件 | `provider.webcatalog` |
| Selenium 子套件 | `provider.webcatalog.selenium` |
| 網頁來源 Provider | `WebCatalogProvider` |
| HTML 解析器 | `WebCatalogHtmlParser` |
| 頁面載入介面 | `WebCatalogPageLoader` |
| 設定 Properties | `WebCatalogProperties` |
| 來源不可用例外 | `WebCatalogProviderUnavailableException` |
| WebDriver Factory 介面 | `WebCatalogWebDriverFactory` |
| Chrome Factory | `ChromeWebCatalogWebDriverFactory` |
| Selenium Page Loader | `SeleniumWebCatalogPageLoader` |
| 對應測試類別 | 與上述正式類別同名並加上 `Test` |
| Selenium Live Test | `WebCatalogSeleniumLiveTest` |
| 測試 fixture 目錄 | `src/test/resources/webcatalog/` |
| Live Test HTML 輸出 | `target/web-catalog-sample.html` |
| Spring 設定 prefix | `catalog.web-source` |
| Render Base URL 環境變數 | `CATALOG_WEB_SOURCE_BASE_URL` |
| Live Test 開關 | `web-catalog.live-test` |
| Fallback Provider qualifier | `webCatalogProvider` |
| 使用者可見錯誤訊息 | `網頁書目來源` |
| 舊設計／計畫文件名稱 | 將來源名稱片段改為 `web-catalog` |

既有文件內容也必須改用「受保護網頁書目來源」或 `WebCatalog`，不得保留可辨識目標的教學網址、範例設定、命令或說明。

### 禁止字串清單

Repository 根目錄使用不版控的 `.protected-source-markers`，每行保存一個禁止公開的 literal，包括來源品牌各種大小寫形式、網域、Base URL 及其他可直接辨識目標的字串；`.gitignore` 與 `.dockerignore` 都必須排除此檔。

實際禁止字串不得寫入設計文件、實作計畫、測試、掃描腳本參數或 commit message。驗收時由本機檔案載入清單，分別掃描：

- 受版控檔案內容。
- 受版控檔案路徑與檔名。
- 準備推送的 Git refs、commit subject 與 commit body。
- JAR 內容、JAR entry 路徑與 Docker image filesystem。
- 應用程式及 ChromeDriver 啟動／錯誤日誌。
- API 成功與錯誤回應。

除執行中程式外，推送前必須掃描目前工作樹及準備推送的完整 Git 歷史，確認不存在：

- Google API Key 實際值。
- 受保護來源的名稱、網域或 Base URL 實際值。
- 包含上述值的設定檔、測試資料、文件或 commit。
- 含本機設定檔的 Docker build context、JAR 與最終 image layer。

## 8. Render Port 與服務位址

Spring Boot 需接受 Render 注入的 `PORT`：

```yaml
server:
  port: ${PORT:8080}
```

Render 部署完成後會提供類似以下網址：

```text
https://<service-name>.onrender.com/
```

該網址不是祕密，因為它必須存在於 APK 中，仍可能被反編譯取得。

## 9. Android 設定

Android 專案位於獨立 Repository：`C:\Users\peng3\AndroidStudioProjects\PersonalBookshelf`。本節是 Render 後端部署成功後的第二階段，不納入第一份「後端 Render 部署」實作計畫。

- Debug 本機測試可繼續使用 `http://127.0.0.1:8080/` 搭配 `adb reverse`。
- 外部使用版本改用 Render HTTPS Base URL。
- 建議將本機與遠端網址分成不同 Build Type 設定，避免每次手動改程式。
- 保留目前三分鐘 HTTP timeout，涵蓋 Render 約一分鐘的冷啟動及 Selenium 查詢時間。
- 查詢畫面提示首次使用可能需要一至兩分鐘喚醒服務。

Android URL 調整將另立實作計畫，輸入為已驗證可用的 Render HTTPS URL；完成後再執行跨網路實機驗收。

## 10. 資源限制與風險

Render Free instance 為 0.1 CPU、512 MB RAM；Spring Boot 與 headless Chromium 同時執行可能接近記憶體限制，這是本方案最大風險。

第一版控制方式：

- Selenium Page Loader 維持同步化，一次只執行一個瀏覽器查詢。
- 只建立一個 headless WebDriver，服務關閉時正常 `quit()`。
- 初始使用 `-Xms64m -Xmx256m` 限制 JVM 記憶體，替 Chromium 保留空間；依 Render 實測記憶體再調整。
- 若 WebDriver 異常，需能關閉失效 Driver，下一次查詢再重新建立。
- 保留受保護網頁來源的查詢間隔，避免短時間大量存取。
- Render 若因記憶體不足重啟，Android 顯示查詢失敗並保留 ISBN，使用者可稍後重試。

若 512 MB 實測無法穩定承載 Java 與 Chromium，依序考慮：

1. 調低 JVM 記憶體並縮減 Chrome 資源使用。
2. 改用較輕量的頁面載入實作，但保留通用的 `WebCatalogPageLoader` 抽象層。
3. 再評估其他雲端或付費方案；第一版不預先擴大範圍。

## 11. 公開 API 風險

Render URL 是公開端點。通用化命名與環境變數只能避免他人從 GitHub、image 或 API 回應辨識目標，不能阻止 Render、目標網站或網路層得知實際連線目的，也不能阻止他人猜到後端網址後呼叫 API。

本次 MVP 暫不新增登入系統；若未來出現濫用，再加入限流或簡易存取權杖。Android APK 無法安全保存永久祕密，因此存取權杖只能降低偶發濫用，不能視為完整安全機制。

## 12. 驗收標準

- 準備推送的目前工作樹、完整 Git 歷史、分支名稱與 commit message，均不含受保護來源的名稱、網域、Base URL 或 Google API Key。
- 通用化類別、套件、設定鍵、測試、fixture、輸出檔與文件名稱完全符合重新命名矩陣。
- `.protected-source-markers` 同時被 Git 與 Docker 排除，且不出現在任何命令列紀錄或受版控掃描腳本內。
- `.dockerignore` 能阻止本機設定檔進入 build context。
- 建置後的 JAR 與 Docker image 不含本機設定檔、Google API Key，以及受保護來源的名稱、網域或 Base URL。
- `local`、`real` 與預設 Profile 的 Spring wiring 測試符合 Bean 矩陣。
- Docker image 建置成功，單元及整合測試全數通過。
- Chromium 與 ChromeDriver 版本相容，容器內 headless 啟動檢查通過。
- Render 在 `real` Profile 成功啟動，日誌不輸出受保護設定。
- 手動 `curl` 能透過 Render HTTPS 查到真實書目。
- Render 休眠後，首次 `curl` 能完成冷啟動及真實查詢，或得到可判讀的錯誤。

Android 跨網路實機查詢及 CSV 寫入屬於第二階段驗收，不阻擋第一份後端部署計畫完成。

## 13. 實作階段切分

### 第一階段：後端 Render 部署

工作目錄：`C:\C2\01.Projects\P-20260802-個人開發-電子書櫃\book-catalog-api`

包含 Profile、受保護設定、`.dockerignore`、Docker、容器測試、Git 歷史淨化、GitHub 推送、Render 設定與 `curl` 驗收。

### 第二階段：Android 遠端連線

工作目錄：`C:\Users\peng3\AndroidStudioProjects\PersonalBookshelf`

以前一階段產生且已驗證的 Render HTTPS URL 為輸入，調整 Build Type、安裝 APK 並進行行動網路實機驗收。第二階段另立設計／實作計畫，避免跨 Repository 的提交與驗收混在同一份計畫。

## 14. 參考依據

- Render Java/JVM 使用 Docker：<https://render.com/docs/docker>
- Render 環境變數與 Secrets：<https://render.com/docs/configure-environment-variables>
- Render 免費服務休眠與資源限制：<https://render.com/docs/free>
- Render Web Service 公開網址與 Port：<https://render.com/docs/web-services>
