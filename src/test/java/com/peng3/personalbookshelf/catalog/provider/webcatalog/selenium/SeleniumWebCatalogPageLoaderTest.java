package com.peng3.personalbookshelf.catalog.provider.webcatalog.selenium;

import com.peng3.personalbookshelf.catalog.provider.webcatalog.WebCatalogProperties;
import com.peng3.personalbookshelf.catalog.provider.webcatalog.WebCatalogProviderUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.WebElement;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

@ExtendWith(OutputCaptureExtension.class)
class SeleniumWebCatalogPageLoaderTest {

    private static final By BOOK_NAME = By.cssSelector("#GoodsGridDiv .b_name");

    private WebDriver driver;
    private SeleniumWebCatalogPageLoader loader;

    @BeforeEach
    void setUp() {
        driver = mock(WebDriver.class);
        WebDriver.Options options = mock(WebDriver.Options.class);
        WebDriver.Timeouts timeouts = mock(WebDriver.Timeouts.class);
        when(driver.manage()).thenReturn(options);
        when(options.timeouts()).thenReturn(timeouts);
        when(driver.findElement(BOOK_NAME)).thenReturn(mock(WebElement.class));

        WebCatalogWebDriverFactory driverFactory = () -> driver;
        WebCatalogProperties properties = new WebCatalogProperties(
                "https://catalog-source.invalid",
                true,
                Duration.ZERO,
                Duration.ofSeconds(20)
        );
        loader = new SeleniumWebCatalogPageLoader(driverFactory, properties);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " "})
    void shouldRejectMissingBaseUrlWithoutCreatingWebDriver(String baseUrl) {
        WebCatalogWebDriverFactory driverFactory = mock(WebCatalogWebDriverFactory.class);
        WebCatalogProperties properties = new WebCatalogProperties(
                baseUrl,
                true,
                Duration.ZERO,
                Duration.ofSeconds(20)
        );
        SeleniumWebCatalogPageLoader invalidLoader =
                new SeleniumWebCatalogPageLoader(driverFactory, properties);

        assertThatThrownBy(() -> invalidLoader.load("9789861375182"))
                .isInstanceOf(WebCatalogProviderUnavailableException.class)
                .hasMessage("網頁書目來源 Base URL 未設定");
        verifyNoInteractions(driverFactory);
    }

    @Test
    void shouldLoadBookPageHtmlByIsbn() {
        when(driver.getPageSource()).thenReturn("<html>book</html>");

        String html = loader.load("9789861375182");

        assertThat(html).isEqualTo("<html>book</html>");
        verify(driver).get("https://catalog-source.invalid/9789861375182");
        verify(driver).findElement(BOOK_NAME);
        verify(driver).quit();
    }

    @Test
    void shouldDiscardFailedDriverAndCreateAnotherDriverOnNextLoad() {
        WebDriver firstDriver = mockWebDriver();
        WebDriver secondDriver = mockWebDriver();
        WebCatalogWebDriverFactory driverFactory = mock(WebCatalogWebDriverFactory.class);
        when(driverFactory.create()).thenReturn(firstDriver, secondDriver);
        SeleniumWebCatalogPageLoader recoveringLoader =
                new SeleniumWebCatalogPageLoader(driverFactory, properties());
        org.mockito.Mockito.doThrow(new WebDriverException("navigation failed"))
                .when(firstDriver)
                .get("https://catalog-source.invalid/9789861375182");
        when(secondDriver.getPageSource()).thenReturn("<html>recovered</html>");

        assertThatThrownBy(() -> recoveringLoader.load("9789861375182"))
                .isInstanceOf(WebCatalogProviderUnavailableException.class)
                .hasMessageContaining("9789861375182");
        verify(firstDriver).quit();

        assertThat(recoveringLoader.load("9789861375182"))
                .isEqualTo("<html>recovered</html>");
        verify(driverFactory, times(2)).create();
        verify(secondDriver).get("https://catalog-source.invalid/9789861375182");
    }

    @Test
    void shouldPreserveNavigationFailureWhenQuittingFailedDriverAlsoFails() {
        WebDriverException navigationFailure = new WebDriverException("navigation failed");
        org.mockito.Mockito.doThrow(navigationFailure)
                .when(driver)
                .get("https://catalog-source.invalid/9789861375182");
        org.mockito.Mockito.doThrow(new WebDriverException("quit failed"))
                .when(driver)
                .quit();

        WebCatalogProviderUnavailableException thrown = catchThrowableOfType(
                WebCatalogProviderUnavailableException.class,
                () -> loader.load("9789861375182")
        );
        assertThat(thrown)
                .hasMessage("網頁書目來源頁面載入失敗，ISBN：9789861375182");
        assertThat(thrown.getCause()).isSameAs(navigationFailure);
        verify(driver).quit();
    }

    @Test
    void shouldLogSafeDocumentStateWhenBookElementTimesOut(CapturedOutput output) {
        WebDriver diagnosticDriver = mock(
                WebDriver.class,
                withSettings().extraInterfaces(JavascriptExecutor.class)
        );
        WebDriver.Options options = mock(WebDriver.Options.class);
        WebDriver.Timeouts timeouts = mock(WebDriver.Timeouts.class);
        when(diagnosticDriver.manage()).thenReturn(options);
        when(options.timeouts()).thenReturn(timeouts);
        when(diagnosticDriver.findElement(BOOK_NAME))
                .thenThrow(new NoSuchElementException("target missing"));
        when(((JavascriptExecutor) diagnosticDriver).executeScript(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(List.of("complete", 5421L, false));
        SeleniumWebCatalogPageLoader diagnosticLoader = new SeleniumWebCatalogPageLoader(
                () -> diagnosticDriver,
                new WebCatalogProperties(
                        "https://catalog-source.invalid",
                        true,
                        Duration.ZERO,
                        Duration.ZERO
                )
        );

        WebCatalogProviderUnavailableException thrown = catchThrowableOfType(
                WebCatalogProviderUnavailableException.class,
                () -> diagnosticLoader.load("9789861375182")
        );

        assertThat(thrown.getCause()).isInstanceOf(TimeoutException.class);
        assertThat(output)
                .contains("網頁書目來源載入診斷")
                .contains("readyState=complete")
                .contains("htmlLength=5421")
                .contains("targetElementPresent=false")
                .doesNotContain("catalog-source.invalid")
                .doesNotContain("target missing");
        verify(diagnosticDriver).quit();
    }

    @Test
    void shouldPreserveOriginalTimeoutWhenDiagnosticsFail(CapturedOutput output) {
        WebDriver diagnosticDriver = mock(
                WebDriver.class,
                withSettings().extraInterfaces(JavascriptExecutor.class)
        );
        WebDriver.Options options = mock(WebDriver.Options.class);
        WebDriver.Timeouts timeouts = mock(WebDriver.Timeouts.class);
        when(diagnosticDriver.manage()).thenReturn(options);
        when(options.timeouts()).thenReturn(timeouts);
        when(diagnosticDriver.findElement(BOOK_NAME))
                .thenThrow(new NoSuchElementException("target missing"));
        when(((JavascriptExecutor) diagnosticDriver).executeScript(org.mockito.ArgumentMatchers.anyString()))
                .thenThrow(new WebDriverException("sensitive diagnostic message"));
        SeleniumWebCatalogPageLoader diagnosticLoader = new SeleniumWebCatalogPageLoader(
                () -> diagnosticDriver,
                new WebCatalogProperties(
                        "https://catalog-source.invalid",
                        true,
                        Duration.ZERO,
                        Duration.ZERO
                )
        );

        WebCatalogProviderUnavailableException thrown = catchThrowableOfType(
                WebCatalogProviderUnavailableException.class,
                () -> diagnosticLoader.load("9789861375182")
        );

        assertThat(thrown.getCause()).isInstanceOf(TimeoutException.class);
        assertThat(output)
                .contains("網頁書目來源載入診斷不可用")
                .contains("原因類型=WebDriverException")
                .doesNotContain("sensitive diagnostic message")
                .doesNotContain("catalog-source.invalid");
        verify(diagnosticDriver).quit();
    }

    @Test
    void shouldQuitLazyCreatedDriverWhenClosed() {
        when(driver.getPageSource()).thenReturn("<html>book</html>");
        loader.load("9789861375182");

        loader.close();

        verify(driver).quit();
    }

    private WebDriver mockWebDriver() {
        WebDriver mockDriver = mock(WebDriver.class);
        WebDriver.Options options = mock(WebDriver.Options.class);
        WebDriver.Timeouts timeouts = mock(WebDriver.Timeouts.class);
        when(mockDriver.manage()).thenReturn(options);
        when(options.timeouts()).thenReturn(timeouts);
        when(mockDriver.findElement(BOOK_NAME)).thenReturn(mock(WebElement.class));
        return mockDriver;
    }

    private WebCatalogProperties properties() {
        return new WebCatalogProperties(
                "https://catalog-source.invalid",
                true,
                Duration.ZERO,
                Duration.ofSeconds(20)
        );
    }
}
