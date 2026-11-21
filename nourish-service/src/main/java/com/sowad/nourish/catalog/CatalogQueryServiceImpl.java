package com.sowad.nourish.catalog;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sowad.nourish.catalog.repository.JdbcCatalogQueryRepository;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@PreAuthorize("hasAnyRole('admin','operator','viewer')")
public class CatalogQueryServiceImpl implements CatalogQueryService {
  private final JdbcCatalogQueryRepository repository;
  private final ObjectMapper json;

  public CatalogQueryServiceImpl(JdbcCatalogQueryRepository repository, ObjectMapper json) {
    this.repository = repository;
    this.json = json;
  }

  public List<Map<String, Object>> categories() {
    return repository.categories();
  }

  public List<Map<String, Object>> units() {
    return repository.units();
  }

  public Map<String, Object> unit(String id) {
    return units().stream()
        .filter(p -> id.equals(p.get("id")))
        .findFirst()
        .orElseThrow(CatalogFailure::missing);
  }

  public List<Map<String, Object>> products(Map<String, String> filters) {
    return repository.products().stream()
        .filter(
            p ->
                filters.entrySet().stream()
                    .allMatch(
                        f ->
                            switch (f.getKey()) {
                              case "categoryId", "unit", "kind", "id" ->
                                  f.getValue().equals(p.get(f.getKey()));
                              case "archived" ->
                                  f.getValue().equals(String.valueOf(p.get("archived")));
                              case "q" ->
                                  (p.get("name") + " " + p.get("sku"))
                                      .toLowerCase(Locale.ROOT)
                                      .contains(f.getValue().toLowerCase(Locale.ROOT));
                              default -> true;
                            }))
        .toList();
  }

  public Map<String, Object> product(String id) {
    return products(Map.of("id", id)).stream().findFirst().orElseThrow(CatalogFailure::missing);
  }

  public List<Map<String, Object>> feedCatalog() {
    return repository.feed();
  }

  public Map<String, Object> options() {
    try {
      return json.readValue(repository.options(), new TypeReference<Map<String, Object>>() {});
    } catch (java.io.IOException e) {
      throw new IllegalStateException("Invalid catalog options", e);
    }
  }

  public List<Map<String, Object>> packaging(String id) {
    product(id);
    return repository.packaging(id);
  }

  public Map<String, Object> cascade(Map<String, String> f) {
    String kind = f.getOrDefault("category", "");
    String stage = f.getOrDefault("stage", "");
    Map<String, List<String>> stages =
        Map.of(
            "feed",
            List.of("groups", "feedTypes", "products"),
            "medicine",
            List.of("medicineNames", "manufacturers", "medicineForms", "products"),
            "chick",
            List.of("breeds", "grades", "products"),
            "fish",
            List.of("fishTypes", "products"));
    if (!stages.getOrDefault(kind, List.of()).contains(stage))
      throw new CatalogFailure(400, "Choose a valid category and stage.");
    List<Map<String, Object>> selected = products(Map.of("kind", kind, "archived", "false"));
    // Each stage restricts by all preceding selections, preserving the Angular contract.
    List<String> fields =
        switch (kind) {
          case "feed" -> List.of("feedGroup", "feedType");
          case "medicine" -> List.of("name", "manufacturer", "medicineForm");
          case "chick" -> List.of("subtype", "grade");
          default -> List.of("subtype");
        };
    List<String> params =
        switch (kind) {
          case "feed" -> List.of("group", "feedType");
          case "medicine" -> List.of("medicineName", "manufacturer", "medicineForm");
          case "chick" -> List.of("breed", "grade");
          default -> List.of("fishType");
        };
    int depth = stages.get(kind).indexOf(stage);
    for (int i = 0; i < depth; i++) {
      String field = fields.get(i);
      String value = f.get(params.get(i));
      selected =
          selected.stream().filter(p -> value != null && value.equals(p.get(field))).toList();
    }
    List<Map<String, String>> result;
    if (stage.equals("products"))
      result =
          selected.stream()
              .map(
                  p ->
                      Map.of(
                          "value",
                          p.get("id").toString(),
                          "label",
                          kind.equals("feed")
                              ? "["
                                  + Objects.toString(p.get("productCode"), p.get("sku").toString())
                                  + "] "
                                  + p.get("name")
                              : p.get("name").toString()))
              .toList();
    else {
      String field = fields.get(depth);
      result =
          selected.stream()
              .map(p -> Objects.toString(p.get(field), ""))
              .filter(v -> !v.isBlank())
              .distinct()
              .sorted()
              .map(v -> Map.of("value", v, "label", v))
              .toList();
    }
    return Map.of("category", kind, "stage", stage, "options", result);
  }
}
