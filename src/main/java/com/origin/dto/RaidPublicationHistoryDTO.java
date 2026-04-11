package com.origin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RaidPublicationHistoryDTO {
    private Long id;
    private Long raidId;
    private String raidNom;
    private LocalDateTime raidDate;
    private String channelId;
    private String guildId;
    private Long messageId;
    private boolean updated;
    private boolean testPublication;
    private LocalDateTime publishedAt;
    private String messageUrl;
}
