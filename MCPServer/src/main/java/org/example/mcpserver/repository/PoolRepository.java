package org.example.mcpserver.repository;

import org.example.mcpserver.repository.domain.PoolEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PoolRepository extends JpaRepository<PoolEntity, String> {
}
