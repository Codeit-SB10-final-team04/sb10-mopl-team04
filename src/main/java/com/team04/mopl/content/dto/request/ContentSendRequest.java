package com.team04.mopl.content.dto.request;

import com.team04.mopl.common.dto.UserSummary;

public record ContentSendRequest(UserSummary sender, String content) {
}
