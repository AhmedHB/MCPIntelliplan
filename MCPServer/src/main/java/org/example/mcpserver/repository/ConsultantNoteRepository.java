package org.example.mcpserver.repository;

import org.example.mcpserver.repository.domain.ConsultantNoteEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsultantNoteRepository extends JpaRepository<ConsultantNoteEntity, String> {
}
