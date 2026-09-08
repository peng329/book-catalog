package com.peng3.personalbookshelf.catalog.api;

import com.peng3.personalbookshelf.catalog.provider.BookCatalogProvider;
import com.peng3.personalbookshelf.catalog.provider.google.CatalogProviderUnavailableException;
import com.peng3.personalbookshelf.catalog.service.BookLookupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ApiExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        BookCatalogProvider unavailableProvider = isbn -> {
            throw new CatalogProviderUnavailableException("Google Books 服務暫時無法使用");
        };

        mockMvc = MockMvcBuilders.standaloneSetup(
                        new BookLookupController(new BookLookupService(unavailableProvider)))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void shouldReturn502WhenCatalogProviderIsUnavailable() throws Exception {
        mockMvc.perform(post("/api/v1/books/lookup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"isbns\": [\"9786264141802\"] }"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.status").value(502))
                .andExpect(jsonPath("$.title").value("書目服務暫時無法使用"));
    }
}
