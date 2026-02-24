package com.toy.talktalk.domain.chat.dto;

import jakarta.validation.constraints.NotNull;

public record InviteMemberRequest(
        @NotNull Long inviteeId
) {
}
