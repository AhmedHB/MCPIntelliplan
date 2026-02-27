package org.example.mcpserver.repository;

import org.example.mcpserver.repository.domain.AvailabilityEntity;
import org.example.mcpserver.repository.domain.AvailabilityStatus;
import org.example.mcpserver.repository.domain.ConsultantEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

public interface AvailabilityRepository extends JpaRepository<AvailabilityEntity, String> {
    List<AvailabilityEntity> findByConsultant_ConsultantIdAndDateAndStatus(
            String consultantId,
            LocalDate date,
            AvailabilityStatus status
    );

    List<AvailabilityEntity> findByDateAndStatus(
            LocalDate date,
            AvailabilityStatus status
    );

    List<AvailabilityEntity> findByDateAndStatusAndStartTimeLessThanEqualAndEndTimeGreaterThanEqual(
            LocalDate date,
            AvailabilityStatus status,
            LocalTime startTime,
            LocalTime endTime
    );

    List<AvailabilityEntity> findByDateAndConsultant_ConsultantIdIn(LocalDate date, List<String> consultantIds);
    List<AvailabilityEntity> findByConsultant_ConsultantIdInAndDate(Set<String> consultantIds, LocalDate date);

    List<AvailabilityEntity> findByConsultant_ConsultantIdAndDate(
            String consultantId,
            LocalDate date
    );
    boolean existsByConsultant_ConsultantIdAndDateAndStatusAndStartTimeLessThanEqualAndEndTimeGreaterThanEqual(
            String consultantId,
            LocalDate date,
            AvailabilityStatus status,
            LocalTime start,
            LocalTime end
    );

    @Query("""
                select distinct a.consultant
                from AvailabilityEntity a
                where a.date = :date
                  and a.status = :status
            """)
    List<ConsultantEntity> findDistinctConsultantsByDateAndStatus(
            @Param("date") LocalDate date,
            @Param("status") AvailabilityStatus status
    );
}