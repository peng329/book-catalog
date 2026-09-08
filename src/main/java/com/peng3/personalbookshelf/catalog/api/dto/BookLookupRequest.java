package com.peng3.personalbookshelf.catalog.api.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record BookLookupRequest(

        @NotEmpty(message = "isbns 不可為空")
        @Size(max = 50, message = "一次最多查詢 50 筆 ISBN")
        List<
                @Pattern(
                        regexp = "97[89]\\d{10}",
                        message = "ISBN 必須為 978 或 979 開頭的 13 碼數字"
                )
                String
        > isbns
) {
}
