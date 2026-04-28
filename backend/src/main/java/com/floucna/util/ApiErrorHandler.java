package com.floucna.util;

import io.javalin.http.Context;
import java.util.Map;

public final class ApiErrorHandler {

    private ApiErrorHandler() {}

    public static void handle(Context ctx, Exception e) {
        if (e instanceof ApiException apiException) {
            ctx.status(apiException.statusCode()).json(Map.of("error", safeMessage(apiException)));
            return;
        }
        if (e instanceof IllegalArgumentException) {
            ctx.status(400).json(Map.of("error", safeMessage(e)));
            return;
        }
        String message = e.getMessage();
        if (message != null && message.toLowerCase().contains("not found")) {
            ctx.status(404).json(Map.of("error", message));
            return;
        }
        if (message != null && !message.isBlank()) {
            ctx.status(400).json(Map.of("error", message));
            return;
        }
        ctx.status(500).json(Map.of("error", "Internal server error"));
    }

    private static String safeMessage(Exception e) {
        String message = e.getMessage();
        return (message == null || message.isBlank()) ? "Request failed" : message;
    }
}
