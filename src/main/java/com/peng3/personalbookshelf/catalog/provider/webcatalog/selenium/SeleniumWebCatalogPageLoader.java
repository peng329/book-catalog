package com.peng3.personalbookshelf.catalog.provider.webcatalog.selenium;

import com.peng3.personalbookshelf.catalog.provider.webcatalog.WebCatalogPageLoader;
import com.peng3.personalbookshelf.catalog.provider.webcatalog.WebCatalogProperties;
import com.peng3.personalbookshelf.catalog.provider.webcatalog.WebCatalogProviderUnavailableException;
import jakarta.annotation.PreDestroy;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
@Profile({"local", "real"})
public class SeleniumWebCatalogPageLoader implements WebCatalogPageLoader, AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(SeleniumWebCatalogPageLoader.class);
    private static final By BOOK_NAME = By.cssSelector("#GoodsGridDiv .b_name");
    private static final String PAGE_DIAGNOSTICS_SCRIPT = """
            return [
                document.readyState,
                document.documentElement ? document.documentElement.outerHTML.length : 0,
                document.querySelector('#GoodsGridDiv .b_name') !== null
            ];
            """;

    private final WebCatalogWebDriverFactory driverFactory;
    private final WebCatalogProperties properties;
    private WebDriver driver;
    private long lastRequestCompletedAtNanos;

    public SeleniumWebCatalogPageLoader(
            WebCatalogWebDriverFactory driverFactory,
            WebCatalogProperties properties
    ) {
        this.driverFactory = driverFactory;
        this.properties = properties;
    }

    @Override
    public synchronized String load(String isbn) {
        String targetUrl = bookUrl(isbn);
        waitForRequestInterval();
        WebDriver currentDriver = null;
        try {
            currentDriver = getOrCreateDriver();
            currentDriver.get(targetUrl);
            new WebDriverWait(currentDriver, properties.pageTimeout())
                    .until(ExpectedConditions.presenceOfElementLocated(BOOK_NAME));
            return currentDriver.getPageSource();
        } catch (WebDriverException exception) {
            logPageDiagnostics(currentDriver);
            throw new WebCatalogProviderUnavailableException(
                    "網頁書目來源頁面載入失敗，ISBN：" + isbn,
                    exception
            );
        } finally {
            closeDriverQuietly();
            lastRequestCompletedAtNanos = System.nanoTime();
        }
    }

    private void logPageDiagnostics(WebDriver currentDriver) {
        if (!(currentDriver instanceof JavascriptExecutor javascriptExecutor)) {
            return;
        }

        try {
            Object rawResult = javascriptExecutor.executeScript(PAGE_DIAGNOSTICS_SCRIPT);
            if (!(rawResult instanceof List<?> values) || values.size() != 3) {
                LOGGER.warn("網頁書目來源載入診斷不可用；原因類型=UnexpectedResult");
                return;
            }

            String readyState = safeReadyState(values.get(0));
            long htmlLength = values.get(1) instanceof Number number
                    ? Math.max(number.longValue(), 0L)
                    : -1L;
            boolean targetElementPresent = Boolean.TRUE.equals(values.get(2));
            LOGGER.warn(
                    "網頁書目來源載入診斷；readyState={}，htmlLength={}，targetElementPresent={}",
                    readyState,
                    htmlLength,
                    targetElementPresent
            );
        } catch (WebDriverException diagnosticException) {
            LOGGER.warn(
                    "網頁書目來源載入診斷不可用；原因類型={}",
                    diagnosticException.getClass().getSimpleName()
            );
        }
    }

    private String safeReadyState(Object rawReadyState) {
        return switch (String.valueOf(rawReadyState)) {
            case "loading" -> "loading";
            case "interactive" -> "interactive";
            case "complete" -> "complete";
            default -> "unknown";
        };
    }

    private WebDriver getOrCreateDriver() {
        if (driver == null) {
            driver = driverFactory.create();
            driver.manage().timeouts().pageLoadTimeout(properties.pageTimeout());
        }
        return driver;
    }

    private String bookUrl(String isbn) {
        String baseUrl = properties.baseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new WebCatalogProviderUnavailableException(
                    "網頁書目來源 Base URL 未設定",
                    null
            );
        }
        return baseUrl.replaceAll("/+$", "") + "/" + isbn;
    }

    private void waitForRequestInterval() {
        if (lastRequestCompletedAtNanos == 0) {
            return;
        }

        long elapsedNanos = System.nanoTime() - lastRequestCompletedAtNanos;
        long remainingNanos = properties.requestDelay().toNanos() - elapsedNanos;
        if (remainingNanos <= 0) {
            return;
        }

        try {
            TimeUnit.NANOSECONDS.sleep(remainingNanos);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new WebCatalogProviderUnavailableException(
                    "等待網頁書目來源請求間隔時被中斷",
                    exception
            );
        }
    }

    @Override
    @PreDestroy
    public synchronized void close() {
        closeDriverQuietly();
    }

    private void closeDriverQuietly() {
        WebDriver driverToClose = driver;
        driver = null;
        if (driverToClose == null) {
            return;
        }

        try {
            driverToClose.quit();
        } catch (WebDriverException ignored) {
            // Cleanup failure must not replace the original navigation failure.
        }
    }
}
