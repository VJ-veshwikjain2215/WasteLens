package com.wastelens.security;

import java.util.UUID;

public record AuthenticatedUser(
    UUID ownerId,
    String email,
    String role
) {}
