package org.example.mcpserver.service;

import org.example.mcpserver.dto.AvailabilityDTO;
import org.example.mcpserver.repository.AvailabilityRepository;
import org.example.mcpserver.repository.ConsultantRepository;
import org.example.mcpserver.repository.domain.AvailabilityEntity;
import org.example.mcpserver.repository.domain.AvailabilityStatus;
import org.example.mcpserver.service.exception.BadRequestException;
import org.example.mcpserver.service.exception.NotFoundException;
import org.example.mcpserver.service.mapping.AvailabilityMapper;
import org.example.mcpserver.service.mapping.EntityRefMapper;
import org.example.mcpserver.service.validation.ValidationUtils;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Service
@Transactional
public class AvailabilityService {

    private final AvailabilityRepository availabilityRepository;
    private final AvailabilityMapper availabilityMapper;
    private final ConsultantRepository consultantRepository;
    private final EntityRefMapper entityRefMapper;

    public AvailabilityService(AvailabilityRepository availabilityRepository,
                               AvailabilityMapper availabilityMapper,
                               ConsultantRepository consultantRepository,
                               EntityRefMapper entityRefMapper) {
        this.availabilityRepository = availabilityRepository;
        this.availabilityMapper = availabilityMapper;
        this.consultantRepository = consultantRepository;
        this.entityRefMapper = entityRefMapper;
    }

    // ================================
    // CREATE
    // ================================
    @Tool(
            name = "availability_create",
            description = "Create a new availability slot with validation of consultantId, date, time range, and status."
    )
    public AvailabilityDTO create(AvailabilityDTO dto) {

        ValidationUtils.requireNonNull(dto, "availability");

        String availabilityId =
                ValidationUtils.requireNonBlank(dto.availabilityId(), "availabilityId");

        ValidationUtils.requireNonNull(dto.date(), "date");
        ValidationUtils.requireTimeRange(dto.startTime(), dto.endTime(), "startTime", "endTime");
        ValidationUtils.requireNonNull(dto.status(), "status");

        if (availabilityRepository.existsById(availabilityId)) {
            throw new BadRequestException("Availability finns redan: " + availabilityId);
        }

        String consultantId =
                ValidationUtils.requireNonBlank(dto.consultantId(), "consultantId");

        if (!consultantRepository.existsById(consultantId)) {
            throw new BadRequestException("Ogiltig consultantId: " + consultantId);
        }

        AvailabilityEntity entity = availabilityMapper.toEntity(dto);

        AvailabilityEntity saved = availabilityRepository.save(entity);

        return availabilityMapper.toDto(saved);
    }

    // ================================
    // READ BY ID
    // ================================
    @Tool(
            name = "availability_get_by_id",
            description = "Retrieve a single availability slot by its ID."
    )
    @Transactional(readOnly = true)
    public AvailabilityDTO getById(String availabilityId) {

        String id = ValidationUtils.requireNonBlank(availabilityId, "availabilityId");

        AvailabilityEntity entity = availabilityRepository.findById(id)
                .orElseThrow(() ->
                        new NotFoundException("Availability hittades inte: " + id));

        return availabilityMapper.toDto(entity);
    }

    @Tool(
            name = "availability_list_by_date_status",
            description = "List availability slots on a given date filtered by status."
    )
    @Transactional(readOnly = true)
    public List<AvailabilityDTO> listByDateStatus(
            java.time.LocalDate date,
            org.example.mcpserver.repository.domain.AvailabilityStatus status
    ) {
        ValidationUtils.requireNonNull(date, "date");
        ValidationUtils.requireNonNull(status, "status");

        return availabilityRepository.findByDateAndStatus(date, status).stream()
                .map(availabilityMapper::toDto)
                .toList();
    }

    @Tool(
            name = "availability_list_by_consultant_date_status",
            description = "List availability slots for a consultant on a given date filtered by status."
    )
    @Transactional(readOnly = true)
    public List<AvailabilityDTO> listByConsultantDateStatus(
            String consultantId,
            java.time.LocalDate date,
            org.example.mcpserver.repository.domain.AvailabilityStatus status
    ) {
        String cid = ValidationUtils.requireNonBlank(consultantId, "consultantId");
        ValidationUtils.requireNonNull(date, "date");
        ValidationUtils.requireNonNull(status, "status");

        if (!consultantRepository.existsById(cid)) {
            throw new BadRequestException("Ogiltig consultantId: " + cid);
        }

        return availabilityRepository
                .findByConsultant_ConsultantIdAndDateAndStatus(cid, date, status)
                .stream()
                .map(availabilityMapper::toDto)
                .toList();
    }

    @Tool(
            name = "availability_list_covering_range",
            description = "List AVAILABLE availability slots that fully cover the given time range on the given date."
    )
    @Transactional(readOnly = true)
    public List<AvailabilityDTO> listCoveringRange(LocalDate date, LocalTime startTime, LocalTime endTime) {
        ValidationUtils.requireNonNull(date, "date");
        ValidationUtils.requireNonNull(startTime, "startTime");
        ValidationUtils.requireNonNull(endTime, "endTime");
        ValidationUtils.requireTimeRange(startTime, endTime, "startTime", "endTime");

        return availabilityRepository
                .findByDateAndStatusAndStartTimeLessThanEqualAndEndTimeGreaterThanEqual(
                        date, AvailabilityStatus.AVAILABLE, startTime, endTime
                )
                .stream()
                .map(availabilityMapper::toDto)
                .toList();
    }

    @Tool(
            name = "availability_list_by_consultant_date",
            description = "Return all availability slots (all statuses) for a consultant on a specific date (YYYY-MM-DD)."
    )
    @Transactional(readOnly = true)
    public List<AvailabilityDTO> listByConsultantAndDate(String consultantId, LocalDate date) {

        ValidationUtils.requireNonBlank(consultantId, "consultantId");
        ValidationUtils.requireNonNull(date, "date");

        return availabilityRepository
                .findByConsultant_ConsultantIdAndDate(consultantId, date)
                .stream()
                .map(availabilityMapper::toDto)
                .toList();
    }

    @Tool(
            name = "availability_is_free",
            description = "Check if a consultant is available for a requested time interval on a date (within an AVAILABLE slot)."
    )
    @Transactional(readOnly = true)
    public boolean isFree(
            String consultantId,
            java.time.LocalDate date,
            java.time.LocalTime start,
            java.time.LocalTime end
    ) {
        String cid = ValidationUtils.requireNonBlank(consultantId, "consultantId");
        ValidationUtils.requireNonNull(date, "date");
        ValidationUtils.requireTimeRange(start, end, "startTime", "endTime");

        if (!consultantRepository.existsById(cid)) {
            throw new BadRequestException("Ogiltig consultantId: " + cid);
        }

        return availabilityRepository
                .existsByConsultant_ConsultantIdAndDateAndStatusAndStartTimeLessThanEqualAndEndTimeGreaterThanEqual(
                        cid, date, AvailabilityStatus.AVAILABLE, start, end
                );
    }

    // ================================
    // LIST ALL
    // ================================
    @Tool(name = "availability_list", description = "Return a list of all availability slots.")
    @Transactional(readOnly = true)
    public List<AvailabilityDTO> list() {
        return availabilityRepository.findAll()
                .stream()
                .map(availabilityMapper::toDto)
                .toList();
    }

    // ================================
    // UPDATE
    // ================================
    @Tool(
            name = "availability_update",
            description = "Update an availability slot. Only provided fields will be modified."
    )
    public AvailabilityDTO update(String availabilityId, AvailabilityDTO patch) {

        String id = ValidationUtils.requireNonBlank(availabilityId, "availabilityId");
        ValidationUtils.requireNonNull(patch, "availability");

        AvailabilityEntity existing = availabilityRepository.findById(id)
                .orElseThrow(() ->
                        new NotFoundException("Availability hittades inte: " + id));

        // Patch date
        if (patch.date() != null) {
            existing.setDate(patch.date());
        }

        // Patch time range
        if (patch.startTime() != null || patch.endTime() != null) {

            var start = patch.startTime() != null
                    ? patch.startTime()
                    : existing.getStartTime();

            var end = patch.endTime() != null
                    ? patch.endTime()
                    : existing.getEndTime();

            ValidationUtils.requireTimeRange(start, end, "startTime", "endTime");

            existing.setStartTime(start);
            existing.setEndTime(end);
        }

        // Patch status
        if (patch.status() != null) {
            existing.setStatus(patch.status());
        }

        // Patch consultant reference
        if (patch.consultantId() != null) {

            String consultantId =
                    ValidationUtils.requireNonBlank(patch.consultantId(), "consultantId");

            if (!consultantRepository.existsById(consultantId)) {
                throw new BadRequestException("Ogiltig consultantId: " + consultantId);
            }

            existing.setConsultant(
                    entityRefMapper.idToConsultant(consultantId)
            );
        }

        AvailabilityEntity saved = availabilityRepository.save(existing);

        return availabilityMapper.toDto(saved);
    }

    // ================================
    // DELETE
    // ================================
    @Tool(
            name = "availability_delete",
            description = "Delete an availability slot by its ID."
    )
    public void delete(String availabilityId) {

        String id = ValidationUtils.requireNonBlank(availabilityId, "availabilityId");

        if (!availabilityRepository.existsById(id)) {
            throw new NotFoundException("Availability hittades inte: " + id);
        }

        availabilityRepository.deleteById(id);
    }
}