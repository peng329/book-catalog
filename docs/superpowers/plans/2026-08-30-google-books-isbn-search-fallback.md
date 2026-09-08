# Google Books ISBN Search Fallback Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Google Books 以 ISBN 搜尋成功但未回傳 ISBN 識別碼時，安全地採用唯一書目結果。

**Architecture:** 保留明列 `ISBN_13` 的精確比對作為最高優先順序。若沒有精確識別碼，僅在 Google 回傳與目前頁面都唯一時採用該書目；其餘情況保持查無，避免跨版本誤認。

**Tech Stack:** Java 17、Spring Boot 4.1、Spring `RestClient`、JUnit 5、AssertJ、MockRestServiceServer。

---

## 檔案地圖

- `pom.xml`：加入 Spring Boot 的 blocking REST client Starter，讓本機 `local` Profile 可注入 `RestClient.Builder`。
- `src/main/java/com/peng3/personalbookshelf/catalog/provider/google/GoogleBooksVolumesResponse.java`：接收 Google 回應的 `totalItems`。
- `src/main/java/com/peng3/personalbookshelf/catalog/provider/google/GoogleBooksCatalogProvider.java`：實作精確識別碼優先、唯一結果回退的書目選擇規則。
- `src/test/java/com/peng3/personalbookshelf/catalog/provider/google/GoogleBooksCatalogProviderTest.java`：以真實 Google 回應型態建立回歸測試。

### Task 1: 寫出唯一結果回退的失敗測試

**Files:**
- Modify: `src/test/java/com/peng3/personalbookshelf/catalog/provider/google/GoogleBooksCatalogProviderTest.java`

- [x] **Step 1: 新增唯一 OCLC 結果的測試**

加入回應 `totalItems: 1`、唯一 `volumeInfo`、且 `industryIdentifiers` 僅有 `OTHER/OCLC` 的 Mock 回應；斷言 `findByIsbn("9786264141802")` 回傳書名「軟體設計耦合的平衡之道」。

- [x] **Step 2: 新增多筆無精確 ISBN 的保護測試**

加入回應 `totalItems: 2`、兩筆無精確 ISBN 的 Mock 回應；斷言 `findByIsbn("9786264141802")` 回傳 `Optional.empty()`。

- [x] **Step 3: 驗證測試為紅燈**

Run: `./mvnw.cmd test -Dtest=GoogleBooksCatalogProviderTest`

Expected: 唯一 OCLC 結果測試失敗，因現行程式只接受 `ISBN_13`。

### Task 2: 最小化實作回退規則

**Files:**
- Modify: `pom.xml`
- Modify: `src/main/java/com/peng3/personalbookshelf/catalog/provider/google/GoogleBooksVolumesResponse.java`
- Modify: `src/main/java/com/peng3/personalbookshelf/catalog/provider/google/GoogleBooksCatalogProvider.java`

- [x] **Step 1: 補齊應用程式 HTTP Client Starter**

在 `pom.xml` 的 `<dependencies>` 加入：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-restclient</artifactId>
</dependency>
```

不填版本，由 Spring Boot Parent 管理。

- [x] **Step 2: 映射 `totalItems`**

將回應 record 改為：

```java
public record GoogleBooksVolumesResponse(Integer totalItems, List<Item> items) {
```

- [x] **Step 3: 實作選擇規則**

先尋找帶有完全相同 `ISBN_13` 的 `VolumeInfo`。未找到時，僅在 `totalItems` 等於 1、`items` 長度等於 1，且該筆 `volumeInfo` 非空時採用；否則回傳空值。

- [x] **Step 4: 驗證 Provider 測試為綠燈**

Run: `./mvnw.cmd test -Dtest=GoogleBooksCatalogProviderTest`

Expected: 6 項 Google Provider 測試通過。

### Task 3: 整體回歸驗證與提交

**Files:**
- Modify: 上述四個檔案

- [x] **Step 1: 執行所有測試**

Run: `./mvnw.cmd test`

Expected: 全部測試通過。

- [x] **Step 2: 檢查版本控制範圍**

Run: `git status --short` 與 `git diff --check`

Expected: 僅包含 POM、Google Provider、回應 DTO、Provider 測試及本計畫文件。

- [ ] **Step 3: 提交 feature 分支**

```bash
git add pom.xml src/main/java/com/peng3/personalbookshelf/catalog/provider/google src/test/java/com/peng3/personalbookshelf/catalog/provider/google docs/superpowers/plans/2026-08-30-google-books-isbn-search-fallback.md
git commit -m "fix: accept unique Google ISBN search result"
```
