CREATE TABLE IF NOT EXISTS raid_templates (
    id BIGINT NOT NULL AUTO_INCREMENT,
    nom VARCHAR(255) NOT NULL,
    jour_semaine VARCHAR(255) NOT NULL,
    heure VARCHAR(255) NOT NULL,
    channel_id VARCHAR(255) NOT NULL,
    message_id VARCHAR(255) NULL,
    raid_size INT NULL,
    target_tanks INT NULL,
    target_heals INT NULL,
    prefer_mains BIT(1) NULL,
    prioritize_buff_coverage BIT(1) NULL,
    hunters_fill_missing_buffs BIT(1) NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS raids (
    id BIGINT NOT NULL AUTO_INCREMENT,
    nom VARCHAR(255) NOT NULL,
    date DATETIME(6) NOT NULL,
    channel_id VARCHAR(255) NOT NULL,
    template_id BIGINT NULL,
    raid_helper_id VARCHAR(255) NULL,
    discord_message_id BIGINT NULL,
    published_message_id BIGINT NULL,
    published_channel_id VARCHAR(255) NULL,
    last_missing_ping_source_message_id BIGINT NULL,
    last_missing_ping_at DATETIME(6) NULL,
    composition_status VARCHAR(255) NULL,
    composition_locked BIT(1) NULL,
    last_published_at DATETIME(6) NULL,
    last_published_group1_snapshot VARCHAR(255) NULL,
    last_published_group2_snapshot VARCHAR(255) NULL,
    PRIMARY KEY (id),
    CONSTRAINT UK_raids_raid_helper_id UNIQUE (raid_helper_id),
    CONSTRAINT FK_raids_template FOREIGN KEY (template_id) REFERENCES raid_templates (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS joueurs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    discord_id VARCHAR(255) NOT NULL,
    pseudo VARCHAR(255) NOT NULL,
    server_pseudo VARCHAR(255) NOT NULL,
    pseudo_ihm VARCHAR(255) NOT NULL,
    is_raider BIT(1) NOT NULL,
    main_character_id BIGINT NULL,
    PRIMARY KEY (id),
    CONSTRAINT UK_joueurs_discord_id UNIQUE (discord_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS personnages (
    id BIGINT NOT NULL AUTO_INCREMENT,
    nom VARCHAR(255) NOT NULL,
    classe VARCHAR(255) NOT NULL,
    specialisation VARCHAR(255) NOT NULL,
    role VARCHAR(255) NOT NULL,
    main BIT(1) NOT NULL,
    joueur_id BIGINT NULL,
    group1_id BIGINT NULL,
    group2_id BIGINT NULL,
    PRIMARY KEY (id),
    CONSTRAINT FK_personnages_joueur FOREIGN KEY (joueur_id) REFERENCES joueurs (id),
    CONSTRAINT FK_personnages_group1 FOREIGN KEY (group1_id) REFERENCES raids (id),
    CONSTRAINT FK_personnages_group2 FOREIGN KEY (group2_id) REFERENCES raids (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE joueurs
    ADD CONSTRAINT FK_joueurs_main_character
    FOREIGN KEY (main_character_id) REFERENCES personnages (id);

CREATE TABLE IF NOT EXISTS compositions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    raid_id BIGINT NOT NULL,
    personnage_id BIGINT NOT NULL,
    role_assigne VARCHAR(255) NOT NULL,
    ordre_groupe INT NULL,
    commentaire VARCHAR(255) NULL,
    PRIMARY KEY (id),
    CONSTRAINT FK_compositions_raid FOREIGN KEY (raid_id) REFERENCES raids (id),
    CONSTRAINT FK_compositions_personnage FOREIGN KEY (personnage_id) REFERENCES personnages (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS inscriptions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    raid_id BIGINT NOT NULL,
    personnage_id BIGINT NOT NULL,
    statut VARCHAR(255) NOT NULL,
    commentaire VARCHAR(255) NULL,
    PRIMARY KEY (id),
    CONSTRAINT FK_inscriptions_raid FOREIGN KEY (raid_id) REFERENCES raids (id),
    CONSTRAINT FK_inscriptions_personnage FOREIGN KEY (personnage_id) REFERENCES personnages (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS raid_inscriptions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    raid_id BIGINT NOT NULL,
    joueur_id BIGINT NOT NULL,
    statut VARCHAR(255) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT UK_raid_inscriptions UNIQUE (raid_id, joueur_id),
    CONSTRAINT FK_raid_inscriptions_raid FOREIGN KEY (raid_id) REFERENCES raids (id),
    CONSTRAINT FK_raid_inscriptions_joueur FOREIGN KEY (joueur_id) REFERENCES joueurs (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS auto_compose_settings (
    id BIGINT NOT NULL,
    max_raids INT NOT NULL,
    target_tanks INT NOT NULL,
    target_heals INT NOT NULL,
    prefer_mains BIT(1) NOT NULL,
    balance_across_raids BIT(1) NOT NULL,
    prioritize_buff_coverage BIT(1) NOT NULL,
    hunters_fill_missing_buffs BIT(1) NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS raid_import_scheduler_settings (
    id BIGINT NOT NULL,
    enabled BIT(1) NOT NULL,
    day_of_week VARCHAR(255) NOT NULL,
    run_hour INT NOT NULL,
    run_minute INT NOT NULL,
    timezone VARCHAR(255) NOT NULL,
    last_run_at DATETIME(6) NULL,
    last_imported_count INT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS raid_publication_history (
    id BIGINT NOT NULL AUTO_INCREMENT,
    raid_id BIGINT NOT NULL,
    channel_id VARCHAR(255) NOT NULL,
    guild_id VARCHAR(255) NULL,
    message_id BIGINT NOT NULL,
    is_updated BIT(1) NOT NULL,
    is_test_publication BIT(1) NOT NULL,
    published_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT FK_publication_history_raid FOREIGN KEY (raid_id) REFERENCES raids (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX IDX_raids_date ON raids (date);
CREATE INDEX IDX_personnages_joueur ON personnages (joueur_id);
CREATE INDEX IDX_personnages_group1 ON personnages (group1_id);
CREATE INDEX IDX_personnages_group2 ON personnages (group2_id);
CREATE INDEX IDX_compositions_raid ON compositions (raid_id);
CREATE INDEX IDX_compositions_personnage ON compositions (personnage_id);
CREATE INDEX IDX_inscriptions_raid ON inscriptions (raid_id);
CREATE INDEX IDX_inscriptions_personnage ON inscriptions (personnage_id);
CREATE INDEX IDX_publication_history_raid ON raid_publication_history (raid_id);
