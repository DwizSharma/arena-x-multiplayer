package com.arenax.domain;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface CollegeRepository extends JpaRepository<College, String> { Optional<College> findByName(String name); }
