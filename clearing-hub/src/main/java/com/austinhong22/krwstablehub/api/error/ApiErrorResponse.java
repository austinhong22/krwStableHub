package com.austinhong22.krwstablehub.api.error;

import java.time.Instant;

public record ApiErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String code,
        String message,
        String path
) {
}
