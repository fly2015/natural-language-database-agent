package com.metajpa.nlda.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record QueryRequest(
        @NotBlank
        @Size(max = 1000)
        String question
) {
}
