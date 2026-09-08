package com.peng3.personalbookshelf.catalog.api;

import com.peng3.personalbookshelf.catalog.api.dto.BookLookupRequest;
import com.peng3.personalbookshelf.catalog.api.dto.BookLookupResponse;
import com.peng3.personalbookshelf.catalog.service.BookLookupService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/books")
public class BookLookupController {

    private final BookLookupService bookLookupService;

    public BookLookupController(BookLookupService bookLookupService) {
        this.bookLookupService = bookLookupService;
    }

    @PostMapping("/lookup")
    public ResponseEntity<BookLookupResponse> lookup(
            @Valid @RequestBody BookLookupRequest request
    ) {
        return ResponseEntity.ok(bookLookupService.lookup(request.isbns()));
    }
}
