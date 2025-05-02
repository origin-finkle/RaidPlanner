package com.origin.dto;

import lombok.Data;

import java.util.List;

@Data
public class RaidHelperWebhookPayload {
    private Event event;
    private List<Signup> signups;

    @Data
    public static class Event {
        private String id;
        private String title;
    }

    @Data
    public static class Signup {
        private User user;
        private String character;
        private String role;
        private String spec;
        private String status;
        private String clazz; // “class” est un mot réservé → on mappe différemment
    }

    @Data
    public static class User {
        private String id;
        private String name;
    }
}