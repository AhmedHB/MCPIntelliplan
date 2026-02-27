package org.example.mcpserver.repository;



import org.example.mcpserver.repository.domain.ServiceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ServiceRepository extends JpaRepository<ServiceEntity, String> {
}
