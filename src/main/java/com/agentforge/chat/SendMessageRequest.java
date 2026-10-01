package com.agentforge.chat;

import jakarta.validation.constraints.NotBlank;

public record SendMessageRequest(@NotBlank String message) {
}
