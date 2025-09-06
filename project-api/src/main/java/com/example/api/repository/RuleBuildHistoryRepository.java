
package com.example.api.repository;

import com.example.api.entity.RuleBuildHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface RuleBuildHistoryRepository extends JpaRepository<RuleBuildHistory, Long> {
    List<RuleBuildHistory> findByRuleNameOrderByBuiltAtDesc(String ruleName);
    Page<RuleBuildHistory> findByRuleNameOrderByBuiltAtDesc(String ruleName, Pageable pageable);
}
