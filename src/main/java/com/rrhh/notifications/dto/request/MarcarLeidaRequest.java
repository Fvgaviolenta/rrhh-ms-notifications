package com.rrhh.notifications.dto.request;

import jakarta.validation.constraints.NotNull;

public record MarcarLeidaRequest(
        @NotNull(message = "El estado leído es obligatorio")
        Boolean leido
) {
}
