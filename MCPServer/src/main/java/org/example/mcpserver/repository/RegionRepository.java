package org.example.mcpserver.repository;


import org.example.mcpserver.repository.domain.RegionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegionRepository extends JpaRepository<RegionEntity, String> {
}
