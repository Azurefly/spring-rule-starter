package com.example.api.repository;

import com.example.api.entity.RuleMeta;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RuleRepository extends JpaRepository<RuleMeta, Long> {
    Optional<RuleMeta> findByName(String name);
    List<RuleMeta> findAllByOrderByUpdatedAtDesc();
}
