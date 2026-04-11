package com.origin.dto;

import lombok.Data;

@Data
public class MergePersonnageRequestDTO {
    private Long sourcePersonnageId;
    private Long targetPersonnageId;
}
