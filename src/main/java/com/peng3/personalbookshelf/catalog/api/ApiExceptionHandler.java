package com.peng3.personalbookshelf.catalog.api;

import com.peng3.personalbookshelf.catalog.provider.google.CatalogProviderUnavailableException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(CatalogProviderUnavailableException.class)
    public ProblemDetail handleCatalogProviderUnavailable(
            CatalogProviderUnavailableException exception
    ) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_GATEWAY,
                "外部書目服務暫時無法使用，請稍後再試。"
        );
        problem.setTitle("書目服務暫時無法使用");
        return problem;
    }
}
