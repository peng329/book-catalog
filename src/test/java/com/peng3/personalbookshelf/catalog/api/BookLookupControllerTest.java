package com.peng3.personalbookshelf.catalog.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class BookLookupControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldReturnFoundAndNotFoundBooks() throws Exception {
        String requestBody = """
                {
                  "isbns": [
                    "9786264141802",
                    "9789573293343"
                  ]
                }
                """;

        mockMvc.perform(post("/api/v1/books/lookup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.books[0].isbn")
                        .value("9786264141802"))
                .andExpect(jsonPath("$.books[0].found")
                        .value(true))
                .andExpect(jsonPath("$.books[1].isbn")
                        .value("9789573293343"))
                .andExpect(jsonPath("$.books[1].found")
                        .value(false));
    }

    @Test
    void shouldRejectEmptyIsbnList() throws Exception {
        String requestBody = """
                {
                  "isbns": []
                }
                """;

        mockMvc.perform(post("/api/v1/books/lookup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRejectInvalidIsbn() throws Exception {
        String requestBody = """
                {
                  "isbns": [
                    "123"
                  ]
                }
                """;

        mockMvc.perform(post("/api/v1/books/lookup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());
    }

}
