package com.peng3.personalbookshelf.catalog.provider.webcatalog.selenium;

import com.peng3.personalbookshelf.catalog.provider.webcatalog.WebCatalogPageLoader;
import com.peng3.personalbookshelf.catalog.provider.webcatalog.WebCatalogProperties;
import com.peng3.personalbookshelf.catalog.provider.webcatalog.WebCatalogProviderUnavailableException;
import jakarta.annotation.PreDestroy;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@Profile({"local", "real"})
public class SeleniumWebCatalogPageLoader implements WebCatalogPageLoader, AutoCloseable {

    private static final By BOOK_NAME = By.cssSelector("#GoodsGridDiv .b_name");

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
        try {
            WebDriver currentDriver = getOrCreateDriver();
            currentDriver.get(targetUrl);
            new WebDriverWait(currentDriver, properties.pageTimeout())
                    .until(ExpectedConditions.presenceOfElementLocated(BOOK_NAME));
            return currentDriver.getPageSource();
        } catch (WebDriverException exception) {
            throw new WebCatalogProviderUnavailableException(
                    "網頁書目來源頁面載入失敗，ISBN：" + isbn,
                    exception
            );
        } finally {
            closeDriverQuietly();
            lastRequestCompletedAtNanos = System.nanoTime();
        }
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
