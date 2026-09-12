package com.peng3.personalbookshelf.catalog.provider.webcatalog;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.lang.reflect.Parameter;

import static org.assertj.core.api.Assertions.assertThat;

class WebCatalogPropertiesTest {

    @Test
    void shouldExposeGenericPrefixAndOnlyOperationalDefaults() {
        ConfigurationProperties annotation =
                WebCatalogProperties.class.getAnnotation(ConfigurationProperties.class);
        Parameter[] parameters = WebCatalogProperties.class
                .getDeclaredConstructors()[0]
                .getParameters();

        assertThat(annotation.prefix()).isEqualTo("catalog.web-source");
        assertThat(parameters).extracting(Parameter::getName)
                .containsExactly("baseUrl", "headless", "requestDelay", "pageTimeout");
        assertThat(parameters[0].getAnnotation(DefaultValue.class)).isNull();
        assertThat(parameters[1].getAnnotation(DefaultValue.class).value())
                .containsExactly("true");
        assertThat(parameters[2].getAnnotation(DefaultValue.class).value())
                .containsExactly("2s");
        assertThat(parameters[3].getAnnotation(DefaultValue.class).value())
                .containsExactly("5s");
    }
}
