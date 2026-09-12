package com.peng3.personalbookshelf.catalog.provider.webcatalog.selenium;

import com.peng3.personalbookshelf.catalog.provider.webcatalog.WebCatalogProperties;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile({"local", "real"})
public class ChromeWebCatalogWebDriverFactory implements WebCatalogWebDriverFactory {

    private final WebCatalogProperties properties;

    public ChromeWebCatalogWebDriverFactory(WebCatalogProperties properties) {
        this.properties = properties;
    }

    @Override
    public WebDriver create() {
        return new ChromeDriver(createOptions());
    }

    ChromeOptions createOptions() {
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--lang=zh-TW");
        if (properties.headless()) {
            options.addArguments(
                    "--headless=new",
                    "--no-sandbox",
                    "--disable-dev-shm-usage",
                    "--disable-gpu",
                    "--disable-extensions",
                    "--disable-background-networking",
                    "--disable-sync",
                    "--no-first-run",
                    "--renderer-process-limit=1",
                    "--blink-settings=imagesEnabled=false"
            );
        }
        return options;
    }
}
