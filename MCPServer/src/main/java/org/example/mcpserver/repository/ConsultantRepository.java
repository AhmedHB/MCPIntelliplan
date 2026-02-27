package org.example.mcpserver.repository;

import org.example.mcpserver.repository.domain.ConsultantEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConsultantRepository extends JpaRepository<ConsultantEntity, String> {

    List<ConsultantEntity> findAll();
    List<ConsultantEntity> findByFirstNameIgnoreCaseAndLastNameIgnoreCase(String firstName, String lastName);
}