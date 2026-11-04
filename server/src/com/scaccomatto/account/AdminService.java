package com.scaccomatto.account;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

final class AdminService {
    private final AccountRepository repository;

    AdminService(AccountRepository repository) {
        this.repository = repository;
    }

    List<AccountRepository.User> listUsers(AccountRepository.User actor)
            throws SQLException, UnauthorizedException {
        requireAdmin(actor);
        return repository.listUsers();
    }

    void deleteUser(AccountRepository.User actor, UUID userId)
            throws SQLException, UnauthorizedException {
        requireAdmin(actor);
        repository.deleteUser(userId);
    }

    void resetPassword(AccountRepository.User actor, UUID userId, String passwordHash)
            throws SQLException, UnauthorizedException {
        requireAdmin(actor);
        repository.updatePassword(userId, passwordHash);
    }

    void banUser(AccountRepository.User actor, UUID userId)
            throws SQLException, UnauthorizedException {
        requireAdmin(actor);
        repository.setBannedStatus(userId, true);
    }

    private static void requireAdmin(AccountRepository.User actor)
            throws UnauthorizedException {
        if (!actor.isAdmin()) {
            throw new UnauthorizedException("forbidden", "Administrator access is required.");
        }
    }
}
