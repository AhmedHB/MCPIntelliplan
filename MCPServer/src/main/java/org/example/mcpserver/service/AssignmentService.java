package org.example.mcpserver.service;

import org.example.mcpserver.dto.AssignmentDTO;
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
    private final AvailabilityRepository availabilityRepository;

    public AssignmentService(AssignmentRepository assignmentRepository,
                             CustomerRepository customerRepository,
                             ConsultantRepository consultantRepository,
                             ServiceRepository serviceRepository,
                             AvailabilityRepository availabilityRepository,
                             AssignmentMapper assignmentMapper,
                             ConsultantMapper consultantMapper) {
        this.assignmentRepository = assignmentRepository;
        this.customerRepository = customerRepository;
        this.consultantRepository = consultantRepository;
        this.serviceRepository = serviceRepository;
        this.availabilityRepository = availabilityRepository;
        this.assignmentMapper = assignmentMapper;
        this.consultantMapper = consultantMapper;
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
                            .anyMatch(s -> covers(s.getStartTime(), s.getEndTime(), start, end));

                    if (!hasCoveringAvailable) return false;

                    // No overlapping non-AVAILABLE slots
                    boolean hasBlockingOverlap = slots.stream()
                            .filter(s -> s.getStatus() != null && s.getStatus() != AvailabilityStatus.AVAILABLE)
                            .anyMatch(s -> overlaps(s.getStartTime(), s.getEndTime(), start, end));

                    return !hasBlockingOverlap;
                })
                .collect(toSet());

        if (conflictFreeCandidateIds.isEmpty()) return List.of();

        // 5) Load consultants and filter region + restrictions + service
        return consultantRepository.findAllById(conflictFreeCandidateIds).stream()
                .filter(Objects::nonNull)
                .filter(c -> consultantRegionsContains(c.getRegions(), customerRegion))
                .filter(c -> !restrictionsContainsCustomer(c.getRestrictions(), customerId))
                .filter(c -> consultantServicesContains(c.getServices(), requiredService))
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

    // -------------------- helpers --------------------

    private static boolean covers(LocalTime slotStart, LocalTime slotEnd, LocalTime aStart, LocalTime aEnd) {
        if (slotStart == null || slotEnd == null || aStart == null || aEnd == null) return false;
        return !slotStart.isAfter(aStart) && !slotEnd.isBefore(aEnd);
    }

    // overlap if intervals intersect: [slotStart, slotEnd) intersects [aStart, aEnd)
    // If you treat end as inclusive in your business rules, tell me and I’ll adjust.
    private static boolean overlaps(LocalTime slotStart, LocalTime slotEnd, LocalTime aStart, LocalTime aEnd) {
        if (slotStart == null || slotEnd == null || aStart == null || aEnd == null) return false;
        return slotStart.isBefore(aEnd) && slotEnd.isAfter(aStart);
    }

    private static List<String> splitSemicolon(String s) {
        if (s == null) return List.of();
        String t = s.trim();
        if (t.isBlank()) return List.of();

        return Arrays.stream(t.split(";"))
                .map(String::trim)
                .filter(x -> !x.isBlank())
                .toList();
    }

    private static boolean consultantRegionsContains(String consultantRegions, String customerRegion) {
        if (customerRegion == null || customerRegion.isBlank()) return false;
        String needle = customerRegion.trim().toLowerCase();

        return splitSemicolon(consultantRegions).stream()
                .map(r -> r.toLowerCase())
                .anyMatch(r -> r.equals(needle));
    }

    private static boolean restrictionsContainsCustomer(String restrictions, String customerId) {
        if (customerId == null || customerId.isBlank()) return false;
        String needle = customerId.trim().toLowerCase();

        return splitSemicolon(restrictions).stream()
                .map(x -> x.toLowerCase())
                .anyMatch(x -> x.equals(needle));
    }

    private static boolean consultantServicesContains(String consultantServices, String requiredService) {
        if (requiredService == null || requiredService.isBlank()) return false;
        String needle = requiredService.trim().toLowerCase();

        return splitSemicolon(consultantServices).stream()
                .map(s -> s.toLowerCase())
                .anyMatch(s -> s.equals(needle));
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