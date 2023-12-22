package ru.shop.backend.search.api.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import ru.shop.backend.search.api.SearchControllerApi;
import ru.shop.backend.search.model.*;
import ru.shop.backend.search.repository.ItemDbRepository;
import ru.shop.backend.search.service.SearchService;

@RestController
@RequiredArgsConstructor
public class SearchControllerImpl implements SearchControllerApi {
    private final ItemDbRepository itemDbRepository;
    private final SearchService searchService;

    public SearchResult find(String text, int regionId) {
        return searchService.getSearchResult(regionId, text);
    }

    public SearchResultElastic finds(String text) {
        if (SearchService.isNumeric(text)) {
            Integer itemId = itemDbRepository.findBySku(text).stream().findFirst().orElse(null);
            if (itemId == null) {
                var catalogue = searchService.getByName(text);
                if (!catalogue.isEmpty()) {
                    return new SearchResultElastic(catalogue);
                }
                return new SearchResultElastic(searchService.getAllFull(text));
            }
            try {
                return new SearchResultElastic(searchService.getByItemId(itemId.toString()));
            } catch (Exception e) {
            }
        }
        return new SearchResultElastic(searchService.getAllFull(text));
    }

}
