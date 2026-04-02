package com.fm.be.modules.users;

import com.fm.be.modules.users.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for the users table.
 * Provides lookup methods for authentication and user management.
 */
@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    /**
     * Finds a user by email address (for email/password login).
     *
     * @param email the email address
     * @return the user if found
     */
    Optional<User> findByEmail(String email);

    /**
     * Finds a user by phone number (for OTP login).
     *
     * @param phone the phone number
     * @return the user if found
     */
    Optional<User> findByPhone(String phone);

    /**
     * Checks if a user with the given email already exists.
     *
     * @param email the email address
     * @return true if exists
     */
    boolean existsByEmail(String email);

    /**
     * Checks if a user with the given phone already exists.
     *
     * @param phone the phone number
     * @return true if exists
     */
    boolean existsByPhone(String phone);
}
