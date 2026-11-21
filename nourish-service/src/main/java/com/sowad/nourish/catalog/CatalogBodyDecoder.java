package com.sowad.nourish.catalog;

import com.fasterxml.jackson.databind.*;
import jakarta.validation.*;
import org.springframework.stereotype.Component;

@Component
public class CatalogBodyDecoder {
  private final ObjectMapper json;
  private final Validator validator;

  public CatalogBodyDecoder(ObjectMapper json, Validator validator) {
    this.json = json;
    this.validator = validator;
  }

  public <T> T decode(JsonNode body, Class<T> type) {
    T value;
    try {
      value =
          json.readerFor(type)
              .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
              .without(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
              .readValue(body);
    } catch (java.io.IOException e) {
      throw new CatalogFailure(
          400,
          "Invalid or unsupported fields. Product registration accepts identity only; no prices,"
              + " stock or catalog overrides.");
    }
    if (value == null) throw new CatalogFailure(400, "Request body is required.");
    var errors = validator.validate(value);
    if (!errors.isEmpty()) throw new ConstraintViolationException(errors);
    return value;
  }
}
