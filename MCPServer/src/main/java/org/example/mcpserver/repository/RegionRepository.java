package org.example.mcpserver.repository;


import org.example.mcpserver.repository.domain.RegionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RegionRepository extends JpaRepository<RegionEntity, String> {
    Optional<RegionEntity> findByNameIgnoreCase(String name);
}
