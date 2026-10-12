package com.paynest.user.persistence;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.paynest.user.domain.User;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String normalizedEmail);

    boolean existsByEmail(String normalizedEmail);
}
