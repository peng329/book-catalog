package com.peng3.personalbookshelf.catalog.provider.google;

public class CatalogProviderUnavailableException extends RuntimeException {

    public CatalogProviderUnavailableException(String message) {
        super(message);
    }

    public CatalogProviderUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
