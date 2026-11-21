package com.sowad.nourish.common.exception;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class GlobalExceptionHandlerTest {
    @RestController
    static class ValidationProbe {
        record Command(@NotBlank String name) { }
        @PostMapping("/probe")
        Command process(@Valid @RequestBody Command command) { return command; }
        @GetMapping("/probe")
        void unavailable() {
            throw new DataAccessResourceFailureException("PRIVATE database connection details");
        }
    }

    @Test
    void validatesPayloadAndHidesDatabaseFailureDetails() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new ValidationProbe())
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(post("/probe").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").exists());
        mvc.perform(post("/probe").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mvc.perform(get("/probe"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value(
                        "Database is unavailable or its schema is not ready."));
    }
}
