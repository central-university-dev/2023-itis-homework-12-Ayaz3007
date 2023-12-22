package ru.shop.backend.search.api;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import ru.shop.backend.search.model.SearchResult;
import ru.shop.backend.search.model.SearchResultElastic;

@RequestMapping("/api/search")
@Tag(name = "Поиск", description = "Методы поиска")
public interface SearchControllerApi {
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Возвращает результаты поиска для всплывающего окна",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = SearchResult.class))}),
            @ApiResponse(responseCode = "400", description = "Ошибка обработки",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "Регион не найден",
                    content = @Content)})
    @Parameter(name = "text", description = "Поисковый запрос")
    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    SearchResult find(@RequestParam String text, @CookieValue(name = "regionId", defaultValue = "1") int regionId);

    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Возвращает результаты поиска",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = SearchResult.class))}),
            @ApiResponse(responseCode = "400", description = "Ошибка обработки",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "Регион не найден",
                    content = @Content)})
    @Parameter(name = "text", description = "Поисковый запрос")
    @GetMapping(value = "/by", produces = "application/json;charset=UTF-8")
    @ResponseStatus(HttpStatus.OK)
    SearchResultElastic finds(@RequestParam String text);
}
