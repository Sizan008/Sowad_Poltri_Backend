package com.sowad.nourish.common.masterdata;

import com.fasterxml.jackson.databind.*;
import jakarta.validation.*;
import org.springframework.stereotype.Component;

@Component
public class StrictCommandDecoder {
  private final ObjectMapper mapper;
  private final Validator validator;

  public StrictCommandDecoder(ObjectMapper mapper, Validator validator) {
    this.mapper =
        mapper
            .copy()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
            .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS);
    this.validator = validator;
  }

  public <T> T decode(String body, Class<T> type) {
    T value;
    try {
      value = mapper.readValue(body, type);
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
      throw new MasterDataException(
          400, "Unsupported fields or invalid field types. Use the documented command fields.");
    }
    MasterDataException.require(value != null, "Request body is required.");
    var violations = validator.validate(value);
    if (!violations.isEmpty()) throw new ConstraintViolationException(violations);
    return value;
  }
}
