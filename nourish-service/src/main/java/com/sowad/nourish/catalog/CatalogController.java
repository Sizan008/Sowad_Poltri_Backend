package com.sowad.nourish.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.sowad.nourish.catalog.command.*;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class CatalogController {
  private final CatalogQueryService queries;
  private final CatalogService commands;
  private final CatalogBodyDecoder decoder;

  public CatalogController(
      CatalogQueryService queries, CatalogService commands, CatalogBodyDecoder decoder) {
    this.queries = queries;
    this.commands = commands;
    this.decoder = decoder;
  }

  @GetMapping("/categories")
  public Object categories() {
    return queries.categories();
  }

  @GetMapping("/units")
  public Object units() {
    return queries.units();
  }

  @GetMapping("/units/{id}")
  public Object unit(@PathVariable String id) {
    return queries.unit(id);
  }

  @PostMapping("/units")
  @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
  public Object createUnit(@RequestBody JsonNode body) {
    return commands.createUnit(decoder.decode(body, SaveUnit.class));
  }

  @PutMapping("/units/{id}")
  public Object editUnit(@PathVariable String id, @RequestBody JsonNode body) {
    return commands.editUnit(id, decoder.decode(body, SaveUnit.class));
  }

  @DeleteMapping("/units/{id}")
  @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
  public void deleteUnit(@PathVariable String id) {
    commands.deleteUnit(id);
  }

  @GetMapping("/products")
  public Object products(@RequestParam Map<String, String> filters) {
    return queries.products(filters);
  }

  @GetMapping("/products/{id}")
  public Object product(@PathVariable String id) {
    return queries.product(id);
  }

  @PostMapping("/catalog/products")
  @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
  public Object createProduct(@RequestBody JsonNode body) {
    return commands.createProduct(decoder.decode(body, SaveProduct.class));
  }

  @PutMapping("/catalog/products/{id}")
  public Object editProduct(@PathVariable String id, @RequestBody JsonNode body) {
    return commands.editProduct(id, decoder.decode(body, SaveProduct.class));
  }

  @DeleteMapping("/catalog/products/{id}")
  @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
  public void archiveProduct(@PathVariable String id) {
    commands.archiveProduct(id);
  }

  @GetMapping("/feedPriceCatalog")
  public Object feed() {
    return queries.feedCatalog();
  }

  @GetMapping("/catalogOptions/product-types")
  public Object options() {
    return queries.options();
  }

  @GetMapping("/catalog/cascade-options")
  public Object cascade(@RequestParam Map<String, String> filters) {
    return queries.cascade(filters);
  }

  @GetMapping("/catalog/products/{id}/packaging")
  public Object packages(@PathVariable String id) {
    return queries.packaging(id);
  }

  @PostMapping("/catalog/products/{id}/packaging")
  @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
  public Object addPackage(@PathVariable String id, @RequestBody JsonNode body) {
    return commands.createPackaging(id, decoder.decode(body, SavePackaging.class));
  }

  @DeleteMapping("/catalog/products/{id}/packaging/{packageId}")
  @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
  public void deactivatePackage(@PathVariable String id, @PathVariable String packageId) {
    commands.deactivatePackaging(id, packageId);
  }
}
