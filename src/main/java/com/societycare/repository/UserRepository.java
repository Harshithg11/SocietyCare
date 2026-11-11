package com.societycare.repository;

import com.societycare.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    @EntityGraph(attributePaths = "society")
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsBySocietyId(Long societyId);

    @Override
    @EntityGraph(attributePaths = "society")
    Page<User> findAll(Pageable pageable);
}
