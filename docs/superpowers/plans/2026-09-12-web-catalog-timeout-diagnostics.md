# Web Catalog Timeout Diagnostics Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 Selenium 載入逾時時輸出不含受保護來源資訊的結構診斷。

**Architecture:** 在既有 `SeleniumWebCatalogPageLoader` 捕捉 `WebDriverException` 時、driver 關閉前執行一次安全 JavaScript。診斷只寫入結構值，失敗時仍保留原始例外與既有 Google Books 備援行為。

**Tech Stack:** Java 17、Spring Boot、Selenium、JUnit 5、Mockito、AssertJ

---

### Task 1: 逾時診斷與安全降級

**Files:**
- Modify: `src/test/java/com/peng3/personalbookshelf/catalog/provider/webcatalog/selenium/SeleniumWebCatalogPageLoaderTest.java`
- Modify: `src/main/java/com/peng3/personalbookshelf/catalog/provider/webcatalog/selenium/SeleniumWebCatalogPageLoader.java`

- [ ] **Step 1: 新增失敗測試**

新增測試，模擬 `TimeoutException`，並要求日誌包含 `readyState`、HTML 字元數與目標元素存在狀態。

- [ ] **Step 2: 驗證測試因尚未有診斷而失敗**

Run: `.\mvnw.cmd -B -ntp -Dtest=SeleniumWebCatalogPageLoaderTest test`

Expected: 新增的日誌斷言失敗。

- [ ] **Step 3: 實作最小安全診斷**

在原始 `WebDriverException` 被包裝前，透過 `JavascriptExecutor` 一次取得三項結構值並記錄；診斷失敗時僅記錄例外類型。

- [ ] **Step 4: 新增並通過診斷失敗保護測試**

驗證診斷例外不取代原始 `TimeoutException`，也不輸出診斷例外訊息。

- [ ] **Step 5: 執行完整驗證**

Run: `.\mvnw.cmd -B -ntp test`

Run: `powershell -ExecutionPolicy Bypass -File .\scripts\verify-protected-source-absent.ps1`

Expected: 全部測試通過，公開資訊掃描通過。

- [ ] **Step 6: 檢查差異並提交**

Run: `git diff --check`

提交診斷變更後，推送 `public-master:master` 供 Render 建置；不自動呼叫線上 API。
