package ru.shop.backend.search.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import ru.shop.backend.search.model.*;
import ru.shop.backend.search.repository.ItemDbRepository;
import ru.shop.backend.search.repository.ItemElasticRepository;

import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SearchService {
    private final ItemElasticRepository itemElasticRepository;
    private final ItemDbRepository itemDbRepository;

    private Pageable pageable = PageRequest.of(0, 150);
    private Pageable pageableSmall = PageRequest.of(0, 10);

    public synchronized SearchResult getSearchResult(Integer regionId, String text) {
        List<CatalogueElastic> result = null;
        if (isNumeric(text)) {
            Integer itemId = itemDbRepository.findBySku(text).stream().findFirst().orElse(null);
            if (itemId == null) {
                List<CatalogueElastic> catalogues = getByName(text);
                if (!catalogues.isEmpty()) {
                    result = catalogues;
                }
            }
            else {
                result = getByItemId(itemId.toString());
            }
        }
        if (result == null) {
            result = getAll(text);
        }


        List<Item> items = itemDbRepository.findByIds(regionId,
                        result.stream()
                                .flatMap(category -> category.getItems().stream())
                                .map(ItemElastic::getItemId).collect(Collectors.toList()));

        Set<String> catalogueUrls = new HashSet<>();
        String brand = null;
        if (!result.isEmpty()) {
            brand = result.get(0).getBrand();
        }

        if (brand == null) {
            brand = "";
        }
        brand = brand.toLowerCase(Locale.ROOT);
        String finalBrand = brand;
        List<Category> categories = itemDbRepository.findCatsByIds(items.stream().map(Item::getItemId).collect(Collectors.toList())).stream()
                .map(arr ->
                {
                    if (catalogueUrls.contains(arr[2].toString()))
                        return null;
                    catalogueUrls.add(arr[2].toString());
                    return
                            new Category(arr[0].toString()
                                    , arr[1].toString()
                                    , "/cat/" + arr[2].toString() + (finalBrand.isEmpty() ? "" : "/brands/" + finalBrand)
                                    , "/cat/" + arr[3].toString(), arr[4] == null ? null : arr[4].toString());
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        return new SearchResult(
                items,
                categories,
                !result.isEmpty() ? (List.of(new TypeHelpText(TypeOfQuery.SEE_ALSO,
                        ((result.get(0).getItems().get(0).getType() != null ? result.get(0).getItems().get(0).getType() : "") +
                                " " + (result.get(0).getBrand() != null ? result.get(0).getBrand() : "")).trim()))) : new ArrayList<>()
        );
    }

    public synchronized List<CatalogueElastic> getAll(String text) {
        return getAll(text, pageableSmall);
    }

    public List<CatalogueElastic> getAll(String text, Pageable pageable) {
        String type = "";
        List<ItemElastic> list = new ArrayList<>();
        String brand = "", text2 = text;
        Long catalogueId = null;
        boolean needConvert = true;
        if (isContainErrorChar(text)) {
            text = convert(text);
            needConvert = false;
        }
        if (needConvert && isContainErrorChar(convert(text))) {
            needConvert = false;
        }
        if (text.contains(" "))
            for (String queryWord : text.split("\\s")) {
                list = itemElasticRepository.findAllByBrand(queryWord, pageable);
                if (list.isEmpty() && needConvert) {
                    list = itemElasticRepository.findAllByBrand(convert(text), pageable);
                }
                if (!list.isEmpty()) {
                    text = text.replace(queryWord, "").trim().replace("  ", " ");
                    brand = list.get(0).getBrand();
                    break;

                }

            }
        list = itemElasticRepository.findAllByType(text, pageable);
        if (list.isEmpty() && needConvert) {
            list = itemElasticRepository.findAllByType(convert(text), pageable);
        }
        if (!list.isEmpty()) {
            type = (list.stream().map(ItemElastic::getType).min(Comparator.comparingInt(String::length)).get());
        } else {
            for (String queryWord : text.split("\\s")) {
                list = itemElasticRepository.findAllByType(queryWord, pageable);
                if (list.isEmpty() && needConvert) {
                    list = itemElasticRepository.findAllByType(convert(text), pageable);
                }
                if (!list.isEmpty()) {
                    text = text.replace(queryWord, "");
                    type = (list.stream().map(ItemElastic::getType).min(Comparator.comparingInt(String::length)).get());
                }
            }
        }
        if (brand.isEmpty()) {
            list = itemElasticRepository.findByCatalogue(text, pageable);
            if (list.isEmpty() && needConvert) {
                list = itemElasticRepository.findByCatalogue(convert(text), pageable);
            }
            if (!list.isEmpty()) {
                catalogueId = list.get(0).getCatalogueId();
            }
        }
        text = text.trim();
        if (text.isEmpty() && !brand.isEmpty())
            return Collections.singletonList(new CatalogueElastic(list.get(0).getCatalogue(), list.get(0).getCatalogueId(), null, brand));
        text += "?";
        if (brand.isEmpty()) {
            type += "?";
            if (catalogueId == null) {
                list = itemElasticRepository.findAllByType(text, type, pageable);
                if (list.isEmpty()) {
                    list = itemElasticRepository.findAllByType(convert(text), type, pageable);
                }
            }
            else {
                list = itemElasticRepository.find(text, catalogueId, pageable);
                if (list.isEmpty()) {
                    list = itemElasticRepository.find(convert(text), catalogueId, pageable);
                }
            }

        } else {
            if (type.isEmpty()) {
                list = itemElasticRepository.findAllByBrand(text, brand, pageable);
                if (list.isEmpty()) {
                    list = itemElasticRepository.findAllByBrand(convert(text), brand, pageable);
                }
            } else {
                type += "?";
                list = itemElasticRepository.findAllByTypeAndBrand(text, brand, type, pageable);
                if (list.isEmpty()) {
                    list = itemElasticRepository.findAllByTypeAndBrand(convert(text), brand, type, pageable);
                }
            }
        }

        if (list.isEmpty()) {
            if (text2.contains(" "))
                text = String.join(" ", text.split("\\s"));
            text2 += "?";
            list = itemElasticRepository.findAllNotStrong(text2, pageable);
            if (list.isEmpty() && needConvert) {
                list = itemElasticRepository.findAllByTypeAndBrand(convert(text2), brand, type, pageable);
            }
        }
        return get(list, text, brand);
    }

    private List<CatalogueElastic> get(List<ItemElastic> list, String name, String brand) {
        Map<String, List<ItemElastic>> map = new HashMap<>();
        AtomicReference<ItemElastic> searchedItem = new AtomicReference<>();
        list.forEach(
                i ->
                {
                    if (name.replace("?", "").equals(i.getName())) {
                        searchedItem.set(i);
                    }
                    if (name.replace("?", "").endsWith(i.getName()) && name.replace("?", "").startsWith(i.getType())) {
                        searchedItem.set(i);
                    }
                    if (!map.containsKey(i.getCatalogue())) {
                        map.put(i.getCatalogue(), new ArrayList<>());
                    }
                    map.get(i.getCatalogue()).add(i);
                }
        );
        if (brand.isEmpty())
            brand = null;
        if (searchedItem.get() != null) {
            ItemElastic i = searchedItem.get();
            return Collections.singletonList(new CatalogueElastic(i.getCatalogue(), i.getCatalogueId(), Collections.singletonList(i), brand));
        }
        String finalBrand = brand;
        return map.keySet().stream().map(c ->
                new CatalogueElastic(c, map.get(c).get(0).getCatalogueId(), map.get(c), finalBrand)).collect(Collectors.toList());
    }

    public List<CatalogueElastic> getByName(String num) {
        List<ItemElastic> list;
        list = itemElasticRepository.findAllByName(".*" + num + ".*", pageable);
        return get(list, num, "");
    }

    public List<CatalogueElastic> getByItemId(String itemId) {
        var list = itemElasticRepository.findByItemId(itemId, PageRequest.of(0, 1));
        return Collections.singletonList(new CatalogueElastic(list.get(0).getCatalogue(), list.get(0).getCatalogueId(), list, list.get(0).getBrand()));
    }

    public static String convert(String message) {
        boolean result = message.matches(".*\\p{InCyrillic}.*");
        char[] ru = {'й', 'ц', 'у', 'к', 'е', 'н', 'г', 'ш', 'щ', 'з', 'х', 'ъ', 'ф', 'ы', 'в', 'а', 'п', 'р', 'о', 'л', 'д', 'ж', 'э', 'я', 'ч', 'с', 'м', 'и', 'т', 'ь', 'б', 'ю', '.',
                ' ', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '-'};
        char[] en = {'q', 'w', 'e', 'r', 't', 'y', 'u', 'i', 'o', 'p', '[', ']', 'a', 's', 'd', 'f', 'g', 'h', 'j', 'k', 'l', ';', '"', 'z', 'x', 'c', 'v', 'b', 'n', 'm', ',', '.', '/',
                ' ', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '-'};
        StringBuilder builder = new StringBuilder();

        if (result) {
            for (int i = 0; i < message.length(); i++) {
                for (int j = 0; j < ru.length; j++) {
                    if (message.charAt(i) == ru[j]) {
                        builder.append(en[j]);
                    }
                }
            }
        } else {
            for (int i = 0; i < message.length(); i++) {
                for (int j = 0; j < en.length; j++) {
                    if (message.charAt(i) == en[j]) {
                        builder.append(ru[j]);
                    }
                }
            }
        }
        return builder.toString();
    }

    private Boolean isContainErrorChar(String text) {
        return text.contains("[") || text.contains("]") || text.contains("\"") || text.contains("/") || text.contains(";");
    }

    public static boolean isNumeric(String strNum) {
        if (strNum == null) {
            return false;
        }

        Pattern pattern = Pattern.compile("\\d+");
        return pattern.matcher(strNum).matches();
    }

    public List<CatalogueElastic> getAllFull(String text) {
        return getAll(text, pageable);
    }
}
