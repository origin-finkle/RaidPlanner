CREATE TABLE IF NOT EXISTS raid_group1 (
    raid_id BIGINT NOT NULL,
    personnage_id BIGINT NOT NULL,
    PRIMARY KEY (raid_id, personnage_id),
    CONSTRAINT FK_raid_group1_raid FOREIGN KEY (raid_id) REFERENCES raids (id) ON DELETE CASCADE,
    CONSTRAINT FK_raid_group1_personnage FOREIGN KEY (personnage_id) REFERENCES personnages (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS raid_group2 (
    raid_id BIGINT NOT NULL,
    personnage_id BIGINT NOT NULL,
    PRIMARY KEY (raid_id, personnage_id),
    CONSTRAINT FK_raid_group2_raid FOREIGN KEY (raid_id) REFERENCES raids (id) ON DELETE CASCADE,
    CONSTRAINT FK_raid_group2_personnage FOREIGN KEY (personnage_id) REFERENCES personnages (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX IDX_raid_group1_personnage ON raid_group1 (personnage_id);
CREATE INDEX IDX_raid_group2_personnage ON raid_group2 (personnage_id);

INSERT IGNORE INTO raid_group1 (raid_id, personnage_id)
SELECT group1_id, id
FROM personnages
WHERE group1_id IS NOT NULL;

INSERT IGNORE INTO raid_group2 (raid_id, personnage_id)
SELECT group2_id, id
FROM personnages
WHERE group2_id IS NOT NULL;
