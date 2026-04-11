package com.origin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RaidDiagnosticDTO {

    private Long raidId;
    private String nom;
    private LocalDateTime date;
    private String storedChannelId;
    private Long storedMessageId;
    private String storedRaidHelperId;
    private String publishedChannelId;
    private Long publishedMessageId;
    private RaidMessageDiagnosticDTO storedMessage;
    private RaidMessageDiagnosticDTO resolvedMessage;
    private boolean sourceChanged;
    private List<RaidSignupDiagnosticDTO> liveSignups;
    private List<RaidSignupDiagnosticDTO> snapshotSignups;
    private List<String> liveOnlyPlayers;
    private List<String> snapshotOnlyPlayers;
}
