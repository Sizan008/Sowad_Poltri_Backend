package com.sowad.nourish.notebook;

import com.sowad.nourish.common.masterdata.StrictCommandDecoder;
import com.sowad.nourish.notebook.command.SaveNote;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@Validated
public class NotebookController {
  private final NotebookService service;
  private final NotebookQueries queries;
  private final StrictCommandDecoder decoder;

  public NotebookController(
      NotebookService service, NotebookQueries queries, StrictCommandDecoder decoder) {
    this.service = service;
    this.queries = queries;
    this.decoder = decoder;
  }

  @GetMapping("/notes")
  public ResponseEntity<Object> list(
      @RequestParam(name = "_page", defaultValue = "1") @Min(1) @Max(1000000) int page,
      @RequestParam(name = "_limit", defaultValue = "20") @Min(1) @Max(200) int size,
      @RequestParam(required = false) @Size(max = 120) String q,
      @RequestParam(name = "_sort", defaultValue = "serial") @Pattern(regexp = "serial")
          String sort,
      @RequestParam(name = "_order", defaultValue = "desc") @Pattern(regexp = "asc|desc")
          String order) {
    var result = queries.page(page, size, q, order);
    return ResponseEntity.ok()
        .header("X-Total-Count", result.get("total").toString())
        .body(result.get("items"));
  }

  @GetMapping("/notes/{id}")
  public Object get(@PathVariable String id) {
    return queries.get(id);
  }

  @PostMapping("/transactions/notes")
  @ResponseStatus(HttpStatus.CREATED)
  public Object create(@RequestBody String body) {
    return service.create(decoder.decode(body, SaveNote.class));
  }

  @PutMapping("/transactions/notes/{id}")
  public Object update(@PathVariable String id, @RequestBody String body) {
    return service.update(id, decoder.decode(body, SaveNote.class));
  }

  @DeleteMapping("/transactions/notes/{id}")
  public Object delete(
      @PathVariable String id, @RequestParam(required = false) @Min(0) Long expectedRevision) {
    return service.delete(id, expectedRevision);
  }
}
