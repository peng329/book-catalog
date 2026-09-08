# Render Backend Deployment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 將 Java 17、Spring Boot、Selenium 網頁書目查詢後端安全地部署到 Render Free Web Service，且公開 Repository、建置產物與日誌均無法辨識受保護網頁來源。

**Architecture:** 後端維持 `BookCatalogProvider` 與 `WebCatalogPageLoader` 邊界，將現有特定來源命名全部改為通用 `WebCatalog` 命名；本機用 `local` Profile，Render 用 `real` Profile。Render 連接只有乾淨 root commit 的 GitHub 主分支，使用 Docker 封裝 Java 17、Spring Boot、Chromium 與 ChromeDriver，實際來源 URL 與 Google Key 只由環境變數注入。

**Tech Stack:** Java 17、Spring Boot 4.1、Maven、Selenium、Jsoup、JUnit 5、Mockito、Docker、Render Blueprint、PowerShell、Git。

---

## Scope

本計畫只處理後端 Repository：

`C:\C2\01.Projects\P-20260802-個人開發-電子書櫃\book-catalog-api`

實作分支及隔離工作目錄：

```text
feature/render-deployment
C:\C2\01.Projects\P-20260802-個人開發-電子書櫃\book-catalog-api\.worktrees\render-deployment
```

Android Repository 不在本計畫修改範圍。取得可用 Render HTTPS URL 後，另立 Android Build Type 與實機驗收計畫。

## File Structure

### 新增

- `.dockerignore`：阻止本機設定、Git 資料、禁止字串清單與建置輸出進入 Docker context。
- `.env.example`：記錄 Render 所需環境變數；只讓秘密變數留空，不包含敏感值。
- `Dockerfile`：multi-stage Maven build 與 Java／Chromium runtime。
- `render.yaml`：Render Free Web Service Blueprint，不包含敏感值。
- `scripts/verify-protected-source-absent.ps1`：由本機禁止字串清單驗證內容、路徑、Git 紀錄與建置產物。
- `src/main/java/com/peng3/personalbookshelf/catalog/provider/RealCatalogPropertiesValidator.java`：`local`／`real` 啟動時驗證外部設定。
- `src/main/java/com/peng3/personalbookshelf/catalog/provider/webcatalog/**`：通用化後的網頁書目 Provider、Parser、Properties、Page Loader 與 Selenium Driver Factory。
- `src/test/java/com/peng3/personalbookshelf/catalog/provider/RealCatalogPropertiesValidatorTest.java`：缺少環境設定時的 fail-fast 測試。
- `src/test/java/com/peng3/personalbookshelf/catalog/provider/webcatalog/**`：通用化後的 Provider、Parser、Selenium 與 Live Test。
- `src/test/resources/webcatalog/sample-book-page.html`：通用測試 fixture。

### 修改

- `.gitignore`：排除 `.protected-source-markers`。
- `src/main/resources/application.yaml`：加入 `${PORT:8080}`。
- `src/main/java/com/peng3/personalbookshelf/catalog/provider/MockBookCatalogProvider.java`：只在非 `local` 且非 `real` 時啟用。
- `src/main/java/com/peng3/personalbookshelf/catalog/provider/FallbackBookCatalogProvider.java`：支援 `local`／`real`，改用 `webCatalogProvider` qualifier。
- `src/test/java/com/peng3/personalbookshelf/catalog/provider/MockBookCatalogProviderProfileTest.java`：完整驗證三種 Profile Bean 矩陣。
- 既有設計與計畫文件：移除受保護來源身分、網址與特定命名，檔名改用 `web-catalog`。

### 僅本機、不版控

- `.protected-source-markers`：每行一個禁止公開 literal，並包含目前有效的 Google API Key。
- `src/main/resources/application-local.yaml`：Google Key 與網頁來源 Base URL。
- Git bundle：保存歷史淨化前的本機 Repository。

---

### Task 1: 建立 Git 與 Docker 洩漏防線

**Files:**
- Modify: `.gitignore`
- Create: `.dockerignore`
- Create: `.env.example`
- Local only: `.protected-source-markers`

- [ ] **Step 1: 在 `.gitignore` 加入本機禁止字串清單**

```gitignore
.protected-source-markers
```

- [ ] **Step 2: 建立 `.dockerignore`**

```dockerignore
.git
.gitignore
.worktrees
.env
.env.*
.protected-source-markers
target
src/main/resources/application-local.yml
src/main/resources/application-local.yaml
src/main/resources/application-secret.yml
src/main/resources/application-secret.yaml
**/application-local.yml
**/application-local.yaml
**/application-secret.yml
**/application-secret.yaml
```

- [ ] **Step 3: 建立不含敏感值、只讓秘密變數留空的 `.env.example`**

```dotenv
SPRING_PROFILES_ACTIVE=real
APP_GOOGLE_BOOKS_API_KEY=
CATALOG_WEB_SOURCE_BASE_URL=
PORT=10000
JAVA_TOOL_OPTIONS=-Xms64m -Xmx256m
```

- [ ] **Step 4: 建立本機 `.protected-source-markers`**

使用 `apply_patch` 建立，但不得在命令列參數、受版控文件或 commit message 中顯示實際內容。每行填入一個需禁止公開的品牌文字、大小寫變體、網域、URL 或目前有效的 Google API Key；不得加入空白行。

- [ ] **Step 5: 驗證兩個忽略機制**

Run:

```powershell
git check-ignore -v .protected-source-markers
$requiredDockerIgnores = @(
  '.git', '.gitignore', '.worktrees', '.env', '.env.*',
  '.protected-source-markers', 'target',
  'src/main/resources/application-local.yml',
  'src/main/resources/application-local.yaml',
  'src/main/resources/application-secret.yml',
  'src/main/resources/application-secret.yaml',
  '**/application-local.yml', '**/application-local.yaml',
  '**/application-secret.yml', '**/application-secret.yaml'
)
$dockerIgnore = Get-Content -LiteralPath .dockerignore -Encoding UTF8
$missingDockerIgnores = $requiredDockerIgnores | Where-Object { $_ -notin $dockerIgnore }
if ($missingDockerIgnores) { throw "Missing .dockerignore entries: $($missingDockerIgnores -join ', ')" }
```

Expected:

- Git 回報 `.protected-source-markers` 由 `.gitignore` 排除。
- `.dockerignore` 以精確路徑滿足既定排除範圍，並以遞迴規則攔截其他目錄下的本機與秘密設定檔；實際 Docker image/layer 掃描在 Task 5、Task 6 完成。

- [ ] **Step 6: 安全複製本機私密設定到隔離工作目錄**

先分別以 `Resolve-Path` 確認來源位於後端主工作目錄、目的位於本 worktree，且兩個路徑都以各自已確認的 root 開頭。再使用 `Copy-Item -LiteralPath` 複製已忽略的 `src/main/resources/application-local.yaml`；不得輸出檔案內容。

複製後：

1. 以 `git check-ignore -v src/main/resources/application-local.yaml` 確認來源與目的檔都被忽略。
2. 從現有 production code 取得舊的本機 Base URL，以 `apply_patch` 將它改放到 worktree 的 `catalog.web-source.base-url`；不在任何受版控檔案提供預設值。
3. 只驗證 YAML key 存在，不輸出 value；確認 `git status --short --ignored` 將此檔列為 ignored。
4. 後續整合回主工作目錄時，以同樣方式只新增該 key，不覆寫原有 Google API Key。

- [ ] **Step 7: 執行現有測試**

Run: `.\mvnw.cmd test`

Expected: `Tests run: 25, Failures: 0, Errors: 0, Skipped: 1`。

- [ ] **Step 8: 提交防線檔案**

```powershell
git add .gitignore .dockerignore .env.example
git commit -m "build: protect local deployment configuration"
```

不得加入 `.protected-source-markers`。

---

### Task 2: 將受保護網頁來源完整通用化

**Files:**
- Create: `src/main/java/com/peng3/personalbookshelf/catalog/provider/webcatalog/WebCatalogProvider.java`
- Create: `src/main/java/com/peng3/personalbookshelf/catalog/provider/webcatalog/WebCatalogHtmlParser.java`
- Create: `src/main/java/com/peng3/personalbookshelf/catalog/provider/webcatalog/WebCatalogPageLoader.java`
- Create: `src/main/java/com/peng3/personalbookshelf/catalog/provider/webcatalog/WebCatalogProperties.java`
- Create: `src/main/java/com/peng3/personalbookshelf/catalog/provider/webcatalog/WebCatalogProviderUnavailableException.java`
- Create: `src/main/java/com/peng3/personalbookshelf/catalog/provider/webcatalog/selenium/WebCatalogWebDriverFactory.java`
- Create: `src/main/java/com/peng3/personalbookshelf/catalog/provider/webcatalog/selenium/ChromeWebCatalogWebDriverFactory.java`
- Create: `src/main/java/com/peng3/personalbookshelf/catalog/provider/webcatalog/selenium/SeleniumWebCatalogPageLoader.java`
- Create: corresponding tests under `src/test/java/com/peng3/personalbookshelf/catalog/provider/webcatalog/`
- Create: `src/test/resources/webcatalog/sample-book-page.html`
- Modify: `src/main/java/com/peng3/personalbookshelf/catalog/provider/FallbackBookCatalogProvider.java`
- Modify: `src/test/java/com/peng3/personalbookshelf/catalog/provider/MockBookCatalogProviderProfileTest.java`
- Rename: source-specific historical spec/plan filenames to equivalent `web-catalog` filenames
- Delete: all replaced source-specific Java, test, fixture and document paths reported by the local marker scan

- [ ] **Step 1: 取得需改名的受版控內容與路徑**

Run without placing marker values on the command line:

```powershell
$markers = Get-Content -LiteralPath .protected-source-markers -Encoding UTF8
git grep -I -n -i -F -f .protected-source-markers --
git ls-files | Select-String -SimpleMatch -CaseSensitive:$false -Pattern $markers
```

Expected: 輸出完整列出需要通用化的 production code、tests、fixtures 與舊文件；將結果只保留在終端，不另存成受版控檔案。

- [ ] **Step 2: 先改名測試與 fixture**

依規格重新命名矩陣使用 `git mv`；測試內：

- package 改為 `provider.webcatalog` 或 `provider.webcatalog.selenium`。
- 類別改用 `WebCatalog...`。
- 假網址固定使用 `https://catalog-source.invalid`。
- 設定 prefix 改為 `catalog.web-source`。
- Live Test 開關改為 `web-catalog.live-test`。
- Live Test Base URL 必須由 `catalog.web-source.base-url` 系統屬性取得；缺少時使用 JUnit assumption 跳過，不提供預設值。
- Live Test HTML 輸出改為 `target/web-catalog-sample.html`。

- [ ] **Step 3: 執行測試確認編譯失敗**

Run: `.\mvnw.cmd test`

Expected: FAIL，因新的 `WebCatalog...` production classes 尚未存在。

- [ ] **Step 4: 改名 production code 並更新引用**

依規格矩陣移動至 `provider.webcatalog`，保留原有行為。`WebCatalogProperties` 必須為：

```java
@ConfigurationProperties(prefix = "catalog.web-source")
public record WebCatalogProperties(
        String baseUrl,
        @DefaultValue("true") boolean headless,
        @DefaultValue("2s") Duration requestDelay,
        @DefaultValue("20s") Duration pageTimeout
) {
}
```

不得提供 Base URL 預設值。例外與日誌文字統一為「網頁書目來源」，不得含品牌、網域或完整目標 URL。

- [ ] **Step 5: 更新 Fallback qualifier**

```java
public FallbackBookCatalogProvider(
        @Qualifier("webCatalogProvider") BookCatalogProvider primary,
        @Qualifier("googleBooksCatalogProvider") BookCatalogProvider fallback
) {
    this.primary = primary;
    this.fallback = fallback;
}
```

捕捉例外改為 `WebCatalogProviderUnavailableException`。

- [ ] **Step 6: 通用化舊文件內容與檔名**

所有 marker scan 命中的舊 spec／plan：

- 文件檔名的來源片段改為 `web-catalog`。
- 文件內容改用 `WebCatalog` 或「受保護網頁書目來源」。
- 移除真實網域、URL 與能直接辨識來源的命令。

- [ ] **Step 7: 執行測試與目前工作樹掃描**

Run:

```powershell
.\mvnw.cmd test
$markers = Get-Content -LiteralPath .protected-source-markers -Encoding UTF8
git grep -I -n -i -F -f .protected-source-markers --
git ls-files | Select-String -SimpleMatch -CaseSensitive:$false -Pattern $markers
```

Expected:

- Maven tests PASS。
- 兩個 marker scan 都沒有輸出；`git grep` 的 exit code 為 `1` 代表零命中。

- [ ] **Step 8: 提交通用化重構**

```powershell
git add src/main src/test docs
git diff --cached --check
git commit -m "refactor: generalize protected web catalog source"
```

---

### Task 3: 實作完整 Profile Bean 矩陣與設定 fail-fast

**Files:**
- Modify: `src/main/java/com/peng3/personalbookshelf/catalog/provider/MockBookCatalogProvider.java`
- Modify: `src/main/java/com/peng3/personalbookshelf/catalog/provider/FallbackBookCatalogProvider.java`
- Modify: `src/main/java/com/peng3/personalbookshelf/catalog/provider/google/GoogleBooksCatalogProvider.java`
- Modify: all production classes under `src/main/java/com/peng3/personalbookshelf/catalog/provider/webcatalog/`
- Create: `src/main/java/com/peng3/personalbookshelf/catalog/provider/RealCatalogPropertiesValidator.java`
- Modify: `src/main/resources/application.yaml`
- Modify: `src/test/java/com/peng3/personalbookshelf/catalog/provider/MockBookCatalogProviderProfileTest.java`
- Create: `src/test/java/com/peng3/personalbookshelf/catalog/provider/RealCatalogPropertiesValidatorTest.java`

- [ ] **Step 1: 擴充 Profile wiring 測試**

新增三組情境：

```java
@Test
void shouldUseOnlyMockProviderByDefault() { /* assert mock present, real beans absent */ }

@Test
void shouldUseCompleteRealProviderGraphWithLocalProfile() { /* assert full matrix */ }

@Test
void shouldUseCompleteRealProviderGraphWithRealProfile() { /* same full matrix */ }
```

`local` 與 `real` context 都提供：

```java
new WebCatalogProperties(
        "https://catalog-source.invalid",
        true,
        Duration.ZERO,
        Duration.ofSeconds(20)
)
new GoogleBooksProperties("test-key")
```

驗證 `BookCatalogProvider` 的主要 Bean 是 `FallbackBookCatalogProvider`，且完整存在 Google Provider、Web Provider、Parser、Selenium Loader 與 Chrome Driver Factory。

- [ ] **Step 2: 新增 fail-fast 單元測試**

```java
@Test
void shouldRejectMissingGoogleApiKey() {
    assertThatThrownBy(() -> new RealCatalogPropertiesValidator(
            new GoogleBooksProperties(" "),
            validWebProperties()
    )).isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("APP_GOOGLE_BOOKS_API_KEY");
}

@Test
void shouldRejectMissingWebSourceBaseUrl() {
    assertThatThrownBy(() -> new RealCatalogPropertiesValidator(
            new GoogleBooksProperties("test-key"),
            new WebCatalogProperties(null, true, Duration.ZERO, Duration.ofSeconds(20))
    )).isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("CATALOG_WEB_SOURCE_BASE_URL");
}
```

- [ ] **Step 3: 執行新測試確認失敗**

Run:

```powershell
.\mvnw.cmd "-Dtest=MockBookCatalogProviderProfileTest,RealCatalogPropertiesValidatorTest" test
```

Expected: FAIL，因 Profile 尚未更新且 validator 尚不存在。

- [ ] **Step 4: 更新所有 Profile 條件**

真實 Bean 統一使用：

```java
@Profile({"local", "real"})
```

包含：Fallback、Google、WebCatalog Provider、Parser（若有 Profile）、Selenium Loader 與 Chrome Driver Factory。Mock 使用：

```java
@Profile("!local & !real")
```

- [ ] **Step 5: 建立設定 validator**

```java
@Component
@Profile({"local", "real"})
public class RealCatalogPropertiesValidator {

    public RealCatalogPropertiesValidator(
            GoogleBooksProperties googleProperties,
            WebCatalogProperties webProperties
    ) {
        requireValue(
                googleProperties.apiKey(),
                "APP_GOOGLE_BOOKS_API_KEY 未設定"
        );
        requireValue(
                webProperties.baseUrl(),
                "CATALOG_WEB_SOURCE_BASE_URL 未設定"
        );
    }

    private static void requireValue(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(message);
        }
    }
}
```

不得將實際值拼入錯誤訊息。

- [ ] **Step 6: 加入 Render Port**

`application.yaml`：

```yaml
spring:
  application:
    name: book-catalog-api

server:
  port: ${PORT:8080}
```

- [ ] **Step 7: 執行 Profile 與完整測試**

Run:

```powershell
.\mvnw.cmd "-Dtest=MockBookCatalogProviderProfileTest,RealCatalogPropertiesValidatorTest" test
.\mvnw.cmd test
```

Expected: 全數 PASS；Live Test 仍跳過。

- [ ] **Step 8: 提交 Profile 與設定驗證**

```powershell
git add src/main src/test
git diff --cached --check
git commit -m "feat: add real catalog deployment profile"
```

---

### Task 4: 強化 Selenium 容器參數與 Driver 復原

**Files:**
- Modify: `src/main/java/com/peng3/personalbookshelf/catalog/provider/webcatalog/selenium/ChromeWebCatalogWebDriverFactory.java`
- Modify: `src/main/java/com/peng3/personalbookshelf/catalog/provider/webcatalog/selenium/SeleniumWebCatalogPageLoader.java`
- Create: `src/test/java/com/peng3/personalbookshelf/catalog/provider/webcatalog/selenium/ChromeWebCatalogWebDriverFactoryTest.java`
- Modify: `src/test/java/com/peng3/personalbookshelf/catalog/provider/webcatalog/selenium/SeleniumWebCatalogPageLoaderTest.java`

- [ ] **Step 1: 新增 headless container options 測試**

將 `createOptions()` 保持 package-private，測試 `goog:chromeOptions.args`：

```java
@Test
void shouldUseContainerSafeArgumentsWhenHeadless() {
    ChromeOptions options = factory(true).createOptions();

    assertThat(chromeArguments(options)).contains(
            "--headless=new",
            "--no-sandbox",
            "--disable-dev-shm-usage",
            "--lang=zh-TW"
    );
}
```

- [ ] **Step 2: 新增失效 Driver 重建測試**

第一次 Driver 的 `get()` 丟出 `WebDriverException`，斷言 `quit()` 被呼叫；第二次 `load()` 必須由 Factory 建立新 Driver 並成功回傳 HTML。

- [ ] **Step 3: 執行測試確認失敗**

Run:

```powershell
.\mvnw.cmd "-Dtest=ChromeWebCatalogWebDriverFactoryTest,SeleniumWebCatalogPageLoaderTest" test
```

Expected: FAIL，因容器參數與 Driver reset 尚未實作。

- [ ] **Step 4: 抽出並使用 ChromeOptions**

```java
ChromeOptions createOptions() {
    ChromeOptions options = new ChromeOptions();
    options.addArguments("--lang=zh-TW");
    if (properties.headless()) {
        options.addArguments(
                "--headless=new",
                "--no-sandbox",
                "--disable-dev-shm-usage"
        );
    }
    return options;
}

@Override
public WebDriver create() {
    return new ChromeDriver(createOptions());
}
```

- [ ] **Step 5: WebDriverException 時關閉並清空 Driver**

```java
} catch (WebDriverException exception) {
    closeDriverQuietly();
    throw new WebCatalogProviderUnavailableException(
            "網頁書目來源頁面載入失敗，ISBN：" + isbn,
            exception
    );
}

private void closeDriverQuietly() {
    WebDriver currentDriver = driver;
    driver = null;
    if (currentDriver == null) {
        return;
    }
    try {
        currentDriver.quit();
    } catch (WebDriverException ignored) {
        // 原始載入錯誤優先，不輸出目標網址。
    }
}
```

`close()` 也重用此方法。

- [ ] **Step 6: 執行指定及完整測試**

Run:

```powershell
.\mvnw.cmd "-Dtest=ChromeWebCatalogWebDriverFactoryTest,SeleniumWebCatalogPageLoaderTest" test
.\mvnw.cmd test
```

Expected: 全數 PASS。

- [ ] **Step 7: 提交 Selenium 容器支援**

```powershell
git add src/main src/test
git diff --cached --check
git commit -m "fix: harden web catalog browser lifecycle"
```

---

### Task 5: 建立 Render Docker image 與 Blueprint

**Files:**
- Create: `Dockerfile`
- Create: `render.yaml`

- [ ] **Step 1: 解析並記錄可重現的建置版本**

在撰寫 Dockerfile 前先執行：

1. 以 `docker buildx imagetools inspect` 取得 Maven/Temurin 17 與 Debian Bookworm Slim 的當前 image digest。
2. 在該 Debian image 內執行 `apt-get update` 與 `apt-cache policy chromium chromium-driver`，取得相同倉庫時點的精確 package version。
3. 將兩個 base image digest 與兩個 browser package version 以 literal 寫入 Dockerfile；不得保留 `latest`、未帶 digest 的 tag 或浮動 browser version。

若 Debian 倉庫日後移除舊 package，必須同時更新 Chromium 與 ChromeDriver 版本，重跑本 Task 的版本相容與 headless 實際啟動測試後才能提交。

- [ ] **Step 2: 建立 multi-stage `Dockerfile`**

```dockerfile
FROM maven:3.9.11-eclipse-temurin-17@sha256:<RESOLVED_BUILD_DIGEST> AS build
WORKDIR /workspace
COPY pom.xml ./
RUN mvn -B -ntp dependency:go-offline
COPY src ./src
RUN mvn -B -ntp test package

FROM debian:bookworm-slim@sha256:<RESOLVED_RUNTIME_DIGEST> AS runtime

ARG CHROMIUM_VERSION=<RESOLVED_CHROMIUM_VERSION>
ARG CHROMIUM_DRIVER_VERSION=<RESOLVED_CHROMIUM_DRIVER_VERSION>

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        ca-certificates \
        "chromium=${CHROMIUM_VERSION}" \
        "chromium-driver=${CHROMIUM_DRIVER_VERSION}" \
        fonts-noto-cjk \
        openjdk-17-jre-headless \
    && rm -rf /var/lib/apt/lists/*

RUN chromium_major="$(chromium --version | sed -E 's/[^0-9]*([0-9]+).*/\1/')" \
    && driver_major="$(chromedriver --version | sed -E 's/[^0-9]*([0-9]+).*/\1/')" \
    && test "$chromium_major" = "$driver_major" \
    && chromium --headless=new --no-sandbox --disable-dev-shm-usage --dump-dom about:blank > /tmp/headless-check.html \
    && grep -q '<html' /tmp/headless-check.html \
    && rm /tmp/headless-check.html

RUN useradd --system --uid 10001 --create-home appuser
WORKDIR /app
COPY --from=build /workspace/target/book-catalog-api-*.jar /app/app.jar

USER appuser
EXPOSE 10000
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

上面的 `<RESOLVED_...>` 只是計畫暫代記號；實作時必須先解析，再以 `apply_patch` 寫入真實固定值，否則不得 commit。

- [ ] **Step 3: 建立 `render.yaml`**

```yaml
services:
  - type: web
    name: personal-bookshelf-catalog-api
    runtime: docker
    plan: free
    region: singapore
    autoDeployTrigger: commit
    envVars:
      - key: SPRING_PROFILES_ACTIVE
        value: real
      - key: APP_GOOGLE_BOOKS_API_KEY
        sync: false
      - key: CATALOG_WEB_SOURCE_BASE_URL
        sync: false
      - key: JAVA_TOOL_OPTIONS
        value: -Xms64m -Xmx256m
```

- [ ] **Step 4: 驗證 Dockerfile 與 Blueprint 不含本機值**

Run:

```powershell
$markers = Get-Content -LiteralPath .protected-source-markers -Encoding UTF8
Select-String -Path Dockerfile,render.yaml,.env.example -SimpleMatch -CaseSensitive:$false -Pattern $markers
```

Expected: 無輸出。

- [ ] **Step 5: 建置 Docker image**

Run:

```powershell
docker build --pull -t personal-bookshelf-catalog-api:render .
```

Expected:

- Maven tests 在 build stage PASS。
- Debian 成功安裝 Java 17、Chromium 與 ChromeDriver。
- image 建置完成。

- [ ] **Step 6: 驗證 Browser 版本相容、Java 17 與實際 headless 啟動**

Run:

```powershell
$versions = docker run --rm --entrypoint sh personal-bookshelf-catalog-api:render -c "java -version 2>&1; chromium --version; chromedriver --version"
$versions
# 解析 Chromium/ChromeDriver major version，不相同就 throw。
docker run --rm --entrypoint sh personal-bookshelf-catalog-api:render -c "chromium --headless=new --no-sandbox --disable-dev-shm-usage --dump-dom about:blank"
```

Expected: Java 為 17；Chromium 與 ChromeDriver 主版本完全相同；headless 命令輸出空白頁 HTML 且 exit code 為 `0`。實作時須將版本解析與 major comparison 寫成可執行 PowerShell，不只人工觀察。

- [ ] **Step 7: 使用 Mock Profile 做容器啟動 smoke test**

Run:

```powershell
$containerName = 'personal-bookshelf-render-smoke'
$containerId = $null
try {
  $containerId = docker run -d --name $containerName `
    -p 18080:10000 `
    -e PORT=10000 `
    -e JAVA_TOOL_OPTIONS="-Xms64m -Xmx256m" `
    personal-bookshelf-catalog-api:render
  if ($LASTEXITCODE -ne 0) { throw 'Container start failed' }

  $ready = $false
  for ($attempt = 1; $attempt -le 30; $attempt++) {
    try {
      $response = Invoke-RestMethod `
        -Uri 'http://127.0.0.1:18080/api/v1/books/lookup' `
        -Method Post `
        -ContentType 'application/json' `
        -Body '{"isbns":["9786264141802"]}'
      if ($response.books) { $ready = $true; break }
    } catch {
      Start-Sleep -Seconds 2
    }
  }
  if (-not $ready) { throw 'Container API did not become ready' }
} finally {
  if ($containerId) {
    docker logs $containerName
    docker rm -f $containerName
  }
}
```

此測試不啟用 `local`/`real` Profile，因此不呼叫外部來源；必須自動輪詢 API，並以 `finally` 保證移除已驗證名稱的暫時 container。

- [ ] **Step 8: 執行完整 Maven 測試並提交**

```powershell
.\mvnw.cmd test
git add Dockerfile render.yaml
git diff --cached --check
git commit -m "build: add Render Docker deployment"
```

---

### Task 6: 建立可重複的公開內容驗證器

**Files:**
- Create: `scripts/verify-protected-source-absent.ps1`

- [ ] **Step 1: 建立不含任何實際 marker 的驗證腳本**

腳本介面：

```powershell
param(
    [string]$MarkersFile = ".protected-source-markers",
    [string]$GitRef = "HEAD",
    [string]$JarPath,
    [string]$DockerImage,
    [switch]$ScanWorkingTree,
    [string[]]$TextContent
)
```

必要行為：

1. Marker 檔不存在、空白、有空行或重複值時立即失敗；清單須包含受保護來源識別與目前有效的 Google API Key。
2. 以 `git archive $GitRef` 匯出完整 tree，解壓後用 `rg --text --hidden --no-ignore --fixed-strings --ignore-case -f` 掃描所有內容，並獨立掃描 archive entry 路徑；不得使用會略過 binary 的 `git grep -I`。
3. 使用 `git log $GitRef --format="%H%n%s%n%b"` 掃描 commit subject/body；報錯只顯示「marker 編號」與範圍，不回顯 marker 本身。
4. 若指定 `ScanWorkingTree`，以尊重 `.gitignore` 的 `rg` 掃描工作樹內容，並以 `git ls-files --cached --others --exclude-standard` 掃描路徑；因此本機 marker 與 local YAML 不會被誤掃或洩漏。
5. 若有 `JarPath`，複製為暫時 ZIP 並解壓至 `target/verification/<guid>/jar`，以 `--hidden --no-ignore` 掃描 entry 路徑與全部內容。
6. 若有 `DockerImage`，使用 `docker image save` 匯出完整 image；以 `--hidden --no-ignore` 掃描 manifest、config、repository metadata、每一個解出的 layer 路徑與內容，另掃描 `docker image inspect` 與 `docker history --no-trunc` 的輸出。不得只掃描合併後的 container filesystem。
7. 若有 `TextContent`，在記憶體掃描 API response、Render log 等外部文字，不先輸出原文。
8. 所有外部命令都檢查 exit code；`rg` 的 `1` 僅代表零命中，`2` 以上視為工具失敗。
9. 使用 `try/finally` 移除已驗證位於 `target/verification/` 下的本次 GUID 暫存目錄。
10. 任一命中回傳 exit code `1`；全部乾淨才回傳 `0`。

- [ ] **Step 2: 先以刻意命中的暫時 marker 驗證腳本會失敗**

在 `target/verification-test-markers` 放入一個確定存在於受版控檔案的非敏感字串，執行：

```powershell
.\scripts\verify-protected-source-absent.ps1 `
  -MarkersFile target\verification-test-markers `
  -GitRef HEAD `
  -ScanWorkingTree
```

Expected: exit code `1` 並指出命中範圍，但不回顯 marker。刪除該暫時檔案。

- [ ] **Step 3: 在提交前驗證工作樹，再提交驗證器**

```powershell
.\scripts\verify-protected-source-absent.ps1 `
  -MarkersFile .protected-source-markers `
  -GitRef HEAD `
  -ScanWorkingTree
git add scripts/verify-protected-source-absent.ps1
git diff --cached --check
git commit -m "test: verify protected source stays private"
```

Expected: 工作樹驗證 exit code `0`，且 commit 完成。

- [ ] **Step 4: 使用真正本機 marker 驗證已提交 HEAD、JAR 與完整 image**

```powershell
.\mvnw.cmd package
$jar = Get-ChildItem target\book-catalog-api-*.jar | Select-Object -First 1 -ExpandProperty FullName
.\scripts\verify-protected-source-absent.ps1 `
  -MarkersFile .protected-source-markers `
  -GitRef HEAD `
  -JarPath $jar `
  -DockerImage personal-bookshelf-catalog-api:render
```

Expected: exit code `0`，Git tree/history、JAR、Docker config/history/layers 全部零命中。

---

### Task 7: 建立乾淨公開 root commit

**Files:**
- Local only: `..\book-catalog-api-before-public-20260906.bundle`
- Git ref: `public-master`

本 Task 會建立新的 root commit，改變未來公開分支的歷史基準。即使使用者已核准程式設計，執行前仍必須再次取得「允許建立 bundle 並重建公開歷史」的明確確認。

- [ ] **Step 1: 確認功能分支乾淨且測試通過**

```powershell
git status --short --branch
.\mvnw.cmd test
```

Expected: `feature/render-deployment` 無未提交內容，所有測試 PASS。

- [ ] **Step 2: 建立本機完整 Git bundle 備份**

```powershell
git bundle create ..\book-catalog-api-before-public-20260906.bundle --all
git bundle verify ..\book-catalog-api-before-public-20260906.bundle
```

Expected: bundle 驗證成功。不得將 bundle 放進 Repository 或上傳雲端。

- [ ] **Step 3: 從目前乾淨 tree 建立不含父 commit 的 root commit**

```powershell
$tree = git rev-parse "HEAD^{tree}"
$rootCommit = "feat: initialize deployable book catalog backend" | git commit-tree $tree
git branch public-master $rootCommit
```

Expected: `git rev-list --count public-master` 回傳 `1`。

- [ ] **Step 4: 驗證公開 root commit**

```powershell
.\scripts\verify-protected-source-absent.ps1 `
  -MarkersFile .protected-source-markers `
  -GitRef public-master
git ls-tree -r --name-only public-master
git log public-master --oneline --decorate
```

Expected: marker 驗證 exit code `0`，只有一個通用 commit，路徑與檔名不揭露受保護來源。

- [ ] **Step 5: 在推送前再次停下確認 GitHub Repository URL 與公開／私有設定**

使用者建立空的 GitHub Repository，不加入 README、`.gitignore` 或 License，並提供 remote URL。只推送 `public-master` 至遠端 `master`，不使用 `--all` 或 `--tags`。

- [ ] **Step 6: 設定限制性的預設 push refspec 並推送**

```powershell
git remote add origin <USER_CONFIRMED_GITHUB_URL>
git config remote.origin.push refs/heads/public-master:refs/heads/master
git config remote.origin.tagOpt --no-tags
git push -u origin public-master:master
```

Expected: GitHub 只有乾淨 `master`，沒有舊分支或 Tags。

- [ ] **Step 7: 從 GitHub 重新檢查公開內容**

```powershell
git fetch origin
.\scripts\verify-protected-source-absent.ps1 `
  -MarkersFile .protected-source-markers `
  -GitRef origin/master
git rev-list --count origin/master
```

Expected: 驗證 exit code `0`，commit 數為 `1`。

- [ ] **Step 8: 將乾淨分支設為後續開發基準**

```powershell
git switch public-master
git branch --set-upstream-to=origin/master public-master
git config push.default upstream
git config remote.origin.push refs/heads/public-master:refs/heads/master
git config remote.origin.tagOpt --no-tags
```

Expected: 目前分支為 `public-master`，預設 push 只能更新 `origin/master`。舊 `master`、`feature/render-deployment` 與 bundle 明確標記為「僅本機歷史，不得推送」；往後新功能一律由 `public-master` 分支。

---

### Task 8: 建立 Render Free Web Service 並驗收

**Files:**
- Render Dashboard configuration only
- No repository file changes expected

- [ ] **Step 1: 由 Render Blueprint 連接 GitHub Repository**

依 Render 官方文件安裝 Render CLI，在瀏覽器完成 `render login`，以 `render whoami -o text` 確認帳號；認證資料只留在 CLI 的使用者設定，不得放進 Repository。先執行：

```powershell
render blueprints validate render.yaml -o text
```

驗證通過後，在 Render Dashboard 選擇 New Blueprint，連接上一 Task 的 GitHub Repository，確認：

- Service type：Web Service。
- Runtime：Docker。
- Plan：Free。
- Region：Singapore。
- Branch：`master`。

- [ ] **Step 2: 在 Render Environment 輸入受保護設定**

設定：

```text
SPRING_PROFILES_ACTIVE=real
APP_GOOGLE_BOOKS_API_KEY=<實際 Key>
CATALOG_WEB_SOURCE_BASE_URL=<實際 Base URL>
JAVA_TOOL_OPTIONS=-Xms64m -Xmx256m
```

實際值只貼入 Render Dashboard，不寫入對話、GitHub 或 Blueprint。

- [ ] **Step 3: 驗證首次 Docker build 與啟動日誌**

記錄部署開始 UTC 時間與 Render Service ID。Dashboard 的 build log 用於確認建置成功；服務啟動後以 CLI 取得 runtime/request logs，先放入記憶體並交給禁止字串驗證器，不直接輸出：

```powershell
$logStart = (Get-Date).ToUniversalTime().AddMinutes(-30).ToString('o')
$runtimeLogs = render logs -r <USER_CONFIRMED_SERVICE_ID> --start $logStart --limit 1000 -o text
if ($LASTEXITCODE -ne 0) { throw 'Unable to read Render logs' }
.\scripts\verify-protected-source-absent.ps1 `
  -MarkersFile .protected-source-markers `
  -GitRef HEAD `
  -TextContent @($runtimeLogs)
```

Expected:

- Maven tests PASS。
- Chromium／ChromeDriver 安裝成功。
- Spring Boot 使用 `real` Profile。
- Server 綁定 Render `PORT`。
- runtime/request logs 的程式化掃描 exit code 為 `0`，未出現受保護 marker 或 Google Key。

- [ ] **Step 4: 取得 Render HTTPS URL 並先做非查詢檢查**

記錄 Render 提供的 `https://<service>.onrender.com`，先確認 TCP／HTTP 可到達；不得將該 URL 誤認為祕密。

- [ ] **Step 5: 做單筆真實查詢並先掃描回應**

```powershell
$apiResponse = curl.exe -sS -i -X POST "https://<service>.onrender.com/api/v1/books/lookup" `
  -H "Content-Type: application/json" `
  -d '{"isbns":["<TEST_ISBN>"]}'
if ($LASTEXITCODE -ne 0) { throw 'Render API request failed' }
.\scripts\verify-protected-source-absent.ps1 `
  -MarkersFile .protected-source-markers `
  -GitRef HEAD `
  -TextContent @($apiResponse)
$apiResponse
```

Expected: 禁止字串掃描先通過，再顯示 HTTP 200 與書目資料；成功及錯誤回應均不揭露受保護來源名稱、URL 或 Key。

- [ ] **Step 6: 驗證 Selenium 與 Google fallback**

各選一個能由網頁來源取得、以及需由 Google Books 備援取得的 ISBN；每次回應先以 `TextContent` 掃描。查詢後重新執行 `render logs` 並掃描，確認 fallback 規則正確且回應、runtime/request logs 均無敏感值。

- [ ] **Step 7: 可重現地驗證錯誤回應與錯誤日誌**

傳送故意無法解析的 JSON，擷取完整 HTTP response，但先不輸出：

```powershell
$errorResponse = curl.exe -sS -i -X POST "https://<service>.onrender.com/api/v1/books/lookup" `
  -H "Content-Type: application/json" `
  -d '{'
.\scripts\verify-protected-source-absent.ps1 `
  -MarkersFile .protected-source-markers `
  -GitRef HEAD `
  -TextContent @($errorResponse)
$errorResponse
```

Expected: HTTP 400；掃描先通過，錯誤回應不含來源名稱、URL 或 Key。接著以 `render logs` 擷取此請求之後的 runtime/request logs 並用相同方式掃描，驗證錯誤日誌也為零命中。

- [ ] **Step 8: 驗證休眠後冷啟動**

等待 Render Free Service 自然休眠後再發送單筆查詢，將回應與新增日誌先通過禁止字串掃描，再記錄喚醒與查詢總時間。

Expected: 可接受一至兩分鐘等待；若超過 timeout，錯誤可判讀且無敏感資訊。

- [ ] **Step 9: 後端階段完成條件**

- Render HTTPS API 從外部網路可用。
- Free instance 可承載 Java 與單一 headless Chromium；未發生持續 OOM／重啟。
- 公開 GitHub、Render log、JAR、Docker image 與 API 回應通過禁止字串檢查。
- 記錄已驗證的 Render Base URL，作為下一份 Android 實作計畫的輸入。

若 512 MB 實測持續 OOM，停止 Android 階段，依規格順序先調整 JVM／Chromium 資源；仍不穩定才重新評估平台。

---

## References

- Render Docker deployment: https://render.com/docs/docker
- Render Free instances: https://render.com/docs/free
- Render environment variables and secrets: https://render.com/docs/configure-environment-variables
- Render CLI commands, Blueprint validation and log filters: https://render.com/docs/cli-reference
