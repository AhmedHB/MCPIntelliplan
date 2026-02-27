package org.example.mcpserver.repository;

import org.example.mcpserver.repository.domain.AssignmentEntity;
import org.example.mcpserver.repository.domain.ConsultantEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;

public interface AssignmentRepository extends JpaRepository<AssignmentEntity, String> {
    List<AssignmentEntity> findByStatusIgnoreCase(String status);
    long countByStatusIgnoreCase(String status);
    List<AssignmentEntity> findByDate(LocalDate date);
    long countByDate(LocalDate date);

    @Query("""
       select distinct a.consultant
       from AssignmentEntity a
       where a.date = :date
       """)
    List<ConsultantEntity> findConsultantsByDate(LocalDate date);

    boolean existsByConsultant_ConsultantIdAndDate(String consultantId, LocalDate date);
}
