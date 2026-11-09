package com.moga.khronosdemo.repositories;

import com.moga.khronosdemo.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    boolean existsByPhoneNumber(String phoneNumber);

    boolean existsByUsernameAndIdNot(String username, Long id);

    boolean existsByEmailAndIdNot(String email, Long id);

    boolean existsByPhoneNumberAndIdNot(String phoneNumber, Long id);
}
