package com.origin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RaidMessageDiagnosticDTO {

    private String channelId;
    private String channelName;
    private String guildId;
    private Long messageId;
    private String url;
    private String author;
    private boolean bot;
    private LocalDateTime createdAt;
    private String title;
    private String description;
    private boolean parsedAsRaidHelper;
    private boolean compositionTool;
    private boolean placeholderSignup;
    private String extractedNom;
    private LocalDateTime extractedDate;
    private String raidHelperId;
    private Integer signupLineCount;
    private String linkedChannelId;
    private Long linkedMessageId;
}
