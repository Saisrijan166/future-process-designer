package com.assesswise.processdesigner.repository;

import com.assesswise.processdesigner.domain.AppUser;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

    /** Case-insensitive, matching the lower(email) unique index. */
    @Query("select u from AppUser u where lower(u.email) = lower(:email)")
    Optional<AppUser> findByEmail(@Param("email") String email);

    @Query("select count(u) > 0 from AppUser u where lower(u.email) = lower(:email)")
    boolean existsByEmail(@Param("email") String email);
}
