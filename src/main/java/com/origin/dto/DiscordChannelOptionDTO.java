package com.origin.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class DiscordChannelOptionDTO {
    String id;
    String name;
    String label;
}
