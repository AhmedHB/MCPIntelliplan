package org.example.mcpserver.service;

import org.example.mcpserver.dto.AssignmentDTO;
import org.example.mcpserver.dto.CalendarAssignmentDTO;
import org.example.mcpserver.dto.CalendarAssignmentRowDTO;
import org.example.mcpserver.dto.CalendarConsultantDTO;
import org.example.mcpserver.dto.CalendarConsultantRowDTO;
import org.example.mcpserver.dto.ConsultantDTO;
import org.example.mcpserver.dto.ConsultantSuggestionDTO;
import org.example.mcpserver.repository.*;
import org.example.mcpserver.repository.domain.AssignmentEntity;
import org.example.mcpserver.repository.domain.AvailabilityEntity;
import org.example.mcpserver.repository.domain.AvailabilityStatus;
import org.example.mcpserver.repository.domain.CustomerEntity;
import org.example.mcpserver.service.exception.BadRequestException;
import org.example.mcpserver.service.exception.NotFoundException;
import org.example.mcpserver.service.mapping.AssignmentMapper;
import org.example.mcpserver.service.mapping.ConsultantMapper;
import org.example.mcpserver.service.mapping.CustomerMapper;
import org.example.mcpserver.service.util.CalendarFilterUtils;
import org.example.mcpserver.service.util.CsvTokenUtils;
import org.example.mcpserver.service.util.TimeRangeUtils;
import org.example.mcpserver.service.validation.ValidationUtils;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.function.Function;

import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

@Service
@Transactional
public class AssignmentService {

    private final AssignmentRepository assignmentRepository;
    private final CustomerRepository customerRepository;
    private final ConsultantRepository consultantRepository;
    private final ServiceRepository serviceRepository;
    private final AssignmentMapper assignmentMapper;
    private final ConsultantMapper consultantMapper;
    private final CustomerMapper customerMapper;
    private final AvailabilityRepository availabilityRepository;

    public AssignmentService(AssignmentRepository assignmentRepository,
                             CustomerRepository customerRepository,
                             ConsultantRepository consultantRepository,
                             ServiceRepository serviceRepository,
                             AvailabilityRepository availabilityRepository,
                             AssignmentMapper assignmentMapper,
                             ConsultantMapper consultantMapper,
                             CustomerMapper customerMapper) {
        this.assignmentRepository = assignmentRepository;
        this.customerRepository = customerRepository;
        this.consultantRepository = consultantRepository;
        this.serviceRepository = serviceRepository;
        this.availabilityRepository = availabilityRepository;
        this.assignmentMapper = assignmentMapper;
        this.consultantMapper = consultantMapper;
        this.customerMapper = customerMapper;
    }

    // ================================
    // CREATE
    // ================================
    @Tool(
            name = "assignment_create",
            description = "Create a new assignment with validation of customerId, consultantId, service, date and time range."
    )
    public AssignmentDTO create(AssignmentDTO dto) {

        ValidationUtils.requireNonNull(dto, "assignment");

        String id = ValidationUtils.requireNonBlank(dto.assignmentId(), "assignmentId");
        if (assignmentRepository.existsById(id)) {
            throw new BadRequestException("Assignment finns redan: " + id);
        }

        // Validate customer
        String customerId = ValidationUtils.requireNonBlank(dto.customerId(), "customerId");
        if (!customerRepository.existsById(customerId)) {
            throw new BadRequestException("Ogiltig customerId: " + customerId);
        }

        // Validate consultant
        String consultantId = ValidationUtils.requireNonBlank(dto.consultantId(), "consultantId");
        if (!consultantRepository.existsById(consultantId)) {
            throw new BadRequestException("Ogiltig consultantId: " + consultantId);
        }

        // Validate service
        String service = ValidationUtils.requireNonBlank(dto.service(), "service");
        if (!serviceRepository.existsById(service)) {
            throw new BadRequestException("Ogiltig service: " + service);
        }

        ValidationUtils.requireNonNull(dto.date(), "date");
        ValidationUtils.requireTimeRange(dto.startTime(), dto.endTime(), "startTime", "endTime");
        ValidationUtils.requireNonBlank(dto.status(), "status");

        AssignmentEntity entity = assignmentMapper.toEntity(dto);
        AssignmentEntity saved = assignmentRepository.save(entity);

        return assignmentMapper.toDto(saved);
    }

    // ================================
    // READ BY ID
    // ================================
    @Tool(
            name = "assignment_get_by_id",
            description = "Retrieve a single assignment by its ID."
    )
    @Transactional(readOnly = true)
    public AssignmentDTO getById(String assignmentId) {

        String id = ValidationUtils.requireNonBlank(assignmentId, "assignmentId");

        AssignmentEntity entity = assignmentRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Assignment hittades inte: " + id));

        return assignmentMapper.toDto(entity);
    }

    // ================================
    // READ BY STATUS
    // ================================
    @Tool(
            name = "assignment_find_by_status",
            description = "List assignments filtered by status (case-insensitive)."
    )
    @Transactional(readOnly = true)
    public List<AssignmentDTO> findByStatus(String status) {

        String s = ValidationUtils.requireNonBlank(status, "status");

        return assignmentRepository.findByStatusIgnoreCase(s)
                .stream()
                .map(assignmentMapper::toDto)
                .toList();
    }

    // ================================
    // READ BY STATUS AND COUNT
    // ================================
    @Tool(
            name = "assignment_count_by_status",
            description = "Return the number of assignments with the given status (case-insensitive)."
    )
    @Transactional(readOnly = true)
    public long countByStatus(String status) {
        String s = ValidationUtils.requireNonBlank(status, "status");
        return assignmentRepository.countByStatusIgnoreCase(s);
    }

    // ================================
    // READ BY DATE
    // ================================
    @Tool(
            name = "assignment_find_by_date",
            description = "List assignments for a specific date (YYYY-MM-DD)."
    )
    @Transactional(readOnly = true)
    public List<AssignmentDTO> findByDate(LocalDate date) {

        ValidationUtils.requireNonNull(date, "date");

        return assignmentRepository.findByDate(date)
                .stream()
                .map(assignmentMapper::toDto)
                .toList();
    }

    // ================================
    // READ BY DATE AND COUNT
    // ================================
    @Tool(
            name = "assignment_count_by_date",
            description = "Return the number of assignments for a specific date (YYYY-MM-DD)."
    )
    @Transactional(readOnly = true)
    public long countByDate(LocalDate date) {

        ValidationUtils.requireNonNull(date, "date");

        return assignmentRepository.countByDate(date);
    }

    @Tool(
            name = "assignment_is_consultant_working_on_date",
            description = "Return true if the consultant has at least one assignment on the given date (YYYY-MM-DD)."
    )
    @Transactional(readOnly = true)
    public boolean isConsultantWorkingOnDate(String consultantId, LocalDate date) {
        String id = ValidationUtils.requireNonBlank(consultantId, "consultantId");
        ValidationUtils.requireNonNull(date, "date");
        return assignmentRepository.existsByConsultant_ConsultantIdAndDate(id, date);
    }

    @Tool(
            name = "assignment_find_consultants_by_date",
            description = "Return distinct consultants that have at least one assignment on the given date (YYYY-MM-DD)."
    )
    @Transactional(readOnly = true)
    public List<ConsultantDTO> findConsultantsByDate(LocalDate date) {

        ValidationUtils.requireNonNull(date, "date");

        // Reuse existing repo method findByDate(date)
        // Distinct by consultantId to avoid duplicates
        return assignmentRepository.findByDate(date).stream()
                .map(AssignmentEntity::getConsultant)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toMap(
                        c -> c.getConsultantId(),
                        c -> c,
                        (a, b) -> a,              // keep first if duplicates
                        java.util.LinkedHashMap::new
                ))
                .values()
                .stream()
                .map(consultantMapper::toDto)
                .toList();
    }

    @Tool(
            name = "assignment_suggest_consultants",
            description = "Suggest consultants for an assignment by checking time availability, region match, restrictions, and required service."
    )
    @Transactional(readOnly = true)
    public List<ConsultantSuggestionDTO> suggestConsultants(String assignmentId, int limit) {

        String aid = ValidationUtils.requireNonBlank(assignmentId, "assignmentId");
        int lim = limit <= 0 ? 5 : Math.min(limit, 50);

        // 1) Load assignment
        AssignmentEntity a = assignmentRepository.findById(aid)
                .orElseThrow(() -> new NotFoundException("Assignment not found: " + aid));

        LocalDate date = ValidationUtils.requireNonNull(a.getDate(), "assignment.date");
        LocalTime start = ValidationUtils.requireNonNull(a.getStartTime(), "assignment.startTime");
        LocalTime end = ValidationUtils.requireNonNull(a.getEndTime(), "assignment.endTime");
        ValidationUtils.requireTimeRange(start, end, "assignment.startTime", "assignment.endTime");

        if (a.getCustomer() == null) {
            throw new BadRequestException("Assignment has no customer set: " + aid);
        }

        String customerId = ValidationUtils.requireNonBlank(
                a.getCustomer().getCustomerId(),
                "assignment.customerId"
        );

        String requiredService = ValidationUtils.requireNonBlank(a.getService(), "assignment.service");

        // 2) Load customer + region
        CustomerEntity cust = customerRepository.findById(customerId)
                .orElseThrow(() -> new NotFoundException("Customer not found: " + customerId));

        String customerRegion = ValidationUtils.requireNonBlank(cust.getRegion(), "customer.region");

        // 3) Candidate consultants: must have at least one AVAILABLE slot that fully covers assignment time
        // NOTE: This query is the "single-slot cover" rule.
        List<AvailabilityEntity> coveringAvailableSlots =
                availabilityRepository.findByDateAndStatusAndStartTimeLessThanEqualAndEndTimeGreaterThanEqual(
                        date, AvailabilityStatus.AVAILABLE, start, end
                );

        Set<String> candidateIds = coveringAvailableSlots.stream()
                .filter(Objects::nonNull)
                .map(AvailabilityEntity::getConsultant)
                .filter(Objects::nonNull)
                .map(c -> c.getConsultantId())
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(id -> !id.isBlank())
                .collect(toSet());

        if (candidateIds.isEmpty()) return List.of();

        // 4) Conflict rule (optional but recommended):
        // Exclude candidates that have ANY overlapping slot with a non-AVAILABLE status (BOOKED/SICK/etc).
        //
        // This prevents cases where someone has an AVAILABLE covering slot but also a BOOKED overlap.
        List<AvailabilityEntity> allSlotsForCandidates =
                availabilityRepository.findByConsultant_ConsultantIdInAndDate(candidateIds, date);

        Map<String, List<AvailabilityEntity>> slotsByConsultant = allSlotsForCandidates.stream()
                .filter(Objects::nonNull)
                .filter(s -> s.getConsultant() != null && s.getConsultant().getConsultantId() != null)
                .collect(toMap(
                        s -> s.getConsultant().getConsultantId(),
                        s -> new ArrayList<>(List.of(s)),
                        (left, right) -> {
                            left.addAll(right);
                            return left;
                        }
                ));

        Set<String> conflictFreeCandidateIds = candidateIds.stream()
                .filter(cid -> {
                    List<AvailabilityEntity> slots = slotsByConsultant.getOrDefault(cid, List.of());

                    // Must STILL have at least one covering AVAILABLE slot (defensive)
                    boolean hasCoveringAvailable = slots.stream()
                            .filter(s -> s.getStatus() == AvailabilityStatus.AVAILABLE)
                            .anyMatch(s -> TimeRangeUtils.covers(s.getStartTime(), s.getEndTime(), start, end));

                    if (!hasCoveringAvailable) return false;

                    // No overlapping non-AVAILABLE slots
                    boolean hasBlockingOverlap = slots.stream()
                            .filter(s -> s.getStatus() != null && s.getStatus() != AvailabilityStatus.AVAILABLE)
                            .anyMatch(s -> TimeRangeUtils.overlaps(s.getStartTime(), s.getEndTime(), start, end));

                    return !hasBlockingOverlap;
                })
                .collect(toSet());

        if (conflictFreeCandidateIds.isEmpty()) return List.of();

        // 5) Load consultants and filter region + restrictions + service
        return consultantRepository.findAllById(conflictFreeCandidateIds).stream()
                .filter(Objects::nonNull)
                .filter(c -> CsvTokenUtils.containsIgnoreCaseToken(c.getRegions(), customerRegion))
                .filter(c -> !CsvTokenUtils.containsIgnoreCaseToken(c.getRestrictions(), customerId))
                .filter(c -> CsvTokenUtils.containsIgnoreCaseToken(c.getServices(), requiredService))
                .limit(lim)
                .map(c -> new ConsultantSuggestionDTO(
                        c.getConsultantId(),
                        c.getFirstName(),
                        c.getLastName(),
                        c.getEmploymentType(),
                        c.getRegions(),
                        requiredService
                ))
                .toList();
    }

    @Tool(
            name = "assignment_calendar_search",
            description = "Return assignments for calendar view filtered by optional service, region, status and date range."
    )
    @Transactional(readOnly = true)
    public CalendarAssignmentDTO calendarForAllAssignments(Set<String> services,
                                                           Set<String> regions,
                                                           Set<String> statuses,
                                                           LocalDate fromDate,
                                                           LocalDate toDate) {
        CalendarFilterUtils.validateDateRange(fromDate, toDate);

        Set<String> normalizedServices = CalendarFilterUtils.normalizeFilterValues(services);
        Set<String> normalizedRegions = CalendarFilterUtils.normalizeFilterValues(regions);
        var normalizedStatuses = CalendarFilterUtils.normalizeStatuses(statuses);

        List<CalendarAssignmentRowDTO> rows = assignmentRepository.findAll().stream()
                .filter(a -> CalendarFilterUtils.matchesService(a, normalizedServices))
                .filter(a -> CalendarFilterUtils.matchesRegion(a, normalizedRegions))
                .filter(a -> CalendarFilterUtils.matchesStatus(a, normalizedStatuses))
                .filter(a -> CalendarFilterUtils.matchesDateRange(a, fromDate, toDate))
                .sorted(Comparator
                        .comparing(AssignmentEntity::getDate)
                        .thenComparing(AssignmentEntity::getStartTime)
                        .thenComparing(AssignmentEntity::getAssignmentId))
                .map(a -> new CalendarAssignmentRowDTO(
                        assignmentMapper.toDto(a),
                        consultantMapper.toDto(a.getConsultant()),
                        customerMapper.toDto(a.getCustomer())
                ))
                .toList();

        return new CalendarAssignmentDTO(rows);
    }
    // ================================
    // LIST ALL
    // ================================
    @Tool(
            name = "assignment_list",
            description = "Return a list of all assignments."
    )
    @Transactional(readOnly = true)
    public List<AssignmentDTO> list() {
        return assignmentRepository.findAll()
                .stream()
                .map(assignmentMapper::toDto)
                .toList();
    }

    // ================================
    // UPDATE
    // ================================
    @Tool(
            name = "assignment_update",
            description = "Update an existing assignment. Only provided fields will be modified."
    )
    public AssignmentDTO update(String assignmentId, AssignmentDTO patch) {

        String id = ValidationUtils.requireNonBlank(assignmentId, "assignmentId");
        ValidationUtils.requireNonNull(patch, "assignment");

        AssignmentEntity existing = assignmentRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Assignment hittades inte: " + id));

        // Patch customer
        if (patch.customerId() != null) {
            String customerId = ValidationUtils.requireNonBlank(patch.customerId(), "customerId");
            if (!customerRepository.existsById(customerId)) {
                throw new BadRequestException("Ogiltig customerId: " + customerId);
            }
            existing.setCustomer(assignmentMapper.toEntity(
                    new AssignmentDTO(null, customerId, null, null, null, null, null, null)
            ).getCustomer());
        }

        // Patch consultant
        if (patch.consultantId() != null) {
            String consultantId = ValidationUtils.requireNonBlank(patch.consultantId(), "consultantId");
            if (!consultantRepository.existsById(consultantId)) {
                throw new BadRequestException("Ogiltig consultantId: " + consultantId);
            }
            existing.setConsultant(assignmentMapper.toEntity(
                    new AssignmentDTO(null, null, consultantId, null, null, null, null, null)
            ).getConsultant());
        }

        // Patch service
        if (patch.service() != null) {
            String service = ValidationUtils.requireNonBlank(patch.service(), "service");
            if (!serviceRepository.existsById(service)) {
                throw new BadRequestException("Ogiltig service: " + service);
            }
            existing.setService(service);
        }

        if (patch.date() != null) {
            existing.setDate(patch.date());
        }

        if (patch.startTime() != null || patch.endTime() != null) {
            var start = patch.startTime() != null ? patch.startTime() : existing.getStartTime();
            var end = patch.endTime() != null ? patch.endTime() : existing.getEndTime();
            ValidationUtils.requireTimeRange(start, end, "startTime", "endTime");
            existing.setStartTime(start);
            existing.setEndTime(end);
        }

        if (patch.status() != null) {
            existing.setStatus(ValidationUtils.requireNonBlank(patch.status(), "status"));
        }

        AssignmentEntity saved = assignmentRepository.save(existing);
        return assignmentMapper.toDto(saved);
    }

    // ================================
    // DELETE
    // ================================
    @Tool(
            name = "assignment_delete",
            description = "Delete an assignment by its ID."
    )
    public void delete(String assignmentId) {

        String id = ValidationUtils.requireNonBlank(assignmentId, "assignmentId");

        if (!assignmentRepository.existsById(id)) {
            throw new NotFoundException("Assignment hittades inte: " + id);
        }

        assignmentRepository.deleteById(id);
    }
}
