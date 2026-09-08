package com.peng3.personalbookshelf.catalog.provider.webcatalog.selenium;

import org.openqa.selenium.WebDriver;

@FunctionalInterface
public interface WebCatalogWebDriverFactory {

    WebDriver create();
}
