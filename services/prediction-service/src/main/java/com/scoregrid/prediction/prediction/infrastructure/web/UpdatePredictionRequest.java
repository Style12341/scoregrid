package com.scoregrid.prediction.prediction.infrastructure.web;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

record UpdatePredictionRequest(
        @NotNull(message = "Los goles del local son obligatorios.")
        @Min(value = 0, message = "Los goles no pueden ser negativos.")
        @Max(value = 99, message = "Los goles no pueden superar 99.")
        Integer homeScore,

        @NotNull(message = "Los goles del visitante son obligatorios.")
        @Min(value = 0, message = "Los goles no pueden ser negativos.")
        @Max(value = 99, message = "Los goles no pueden superar 99.")
        Integer awayScore
) {}
