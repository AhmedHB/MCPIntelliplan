package org.example.mcpserver.service;

import org.example.mcpserver.dto.ConsultantDTO;
import org.example.mcpserver.repository.AvailabilityRepository;
import org.example.mcpserver.repository.ConsultantRepository;
import org.example.mcpserver.repository.domain.AvailabilityEntity;
import org.example.mcpserver.repository.domain.AvailabilityStatus;
import org.example.mcpserver.repository.domain.ConsultantEntity;
import org.example.mcpserver.service.exception.BadRequestException;
import org.example.mcpserver.service.exception.NotFoundException;
import org.example.mcpserver.service.mapping.ConsultantMapper;
import org.example.mcpserver.service.validation.ValidationUtils;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Objects;

@Service
@Transactional
public class ConsultantService {

    private final ConsultantRepository consultantRepository;
    private final AvailabilityRepository availabilityRepository;
    private final ConsultantMapper consultantMapper;

    public ConsultantService(ConsultantRepository consultantRepository,
                             AvailabilityRepository availabilityRepository,
                             ConsultantMapper consultantMapper) {
        this.consultantRepository = consultantRepository;
        this.availabilityRepository = availabilityRepository;
        this.consultantMapper = consultantMapper;
    }

    @Tool(
            name = "consultant_create",
            description = "Create a consultant with validation (required: consultantId, firstName, lastName, employmentType). Optional CSV fields are validated to not contain empty items."
    )
    public ConsultantDTO create(ConsultantDTO dto) {
        ValidationUtils.requireNonNull(dto, "consultant");

        String id = ValidationUtils.requireNonBlank(dto.consultantId(), "consultantId");
        if (consultantRepository.existsById(id)) {
            throw new BadRequestException("Consultant finns redan: " + id);
        }

        String firstName = ValidationUtils.requireNonBlank(dto.firstName(), "firstName");
        String lastName = ValidationUtils.requireNonBlank(dto.lastName(), "lastName");
        String employmentType = ValidationUtils.requireNonBlank(dto.employmentType(), "employmentType");

        // Optional fields – trim to null
        String services = ValidationUtils.trimToNull(dto.services());
        String regions = ValidationUtils.trimToNull(dto.regions());
        String pools = ValidationUtils.trimToNull(dto.pools());
        String restrictions = ValidationUtils.trimToNull(dto.restrictions());
        String customerExperience = ValidationUtils.trimToNull(dto.customerExperience());

        // Optional CSV validation (no empty items)
        ValidationUtils.requireCsvNoEmptyItems(services, "services");
        ValidationUtils.requireCsvNoEmptyItems(regions, "regions");
        ValidationUtils.requireCsvNoEmptyItems(pools, "pools");

        // Map DTO -> Entity, then apply validated/normalized values
        ConsultantEntity entity = consultantMapper.toEntity(dto);

        entity.setConsultantId(id);
        entity.setFirstName(firstName);
        entity.setLastName(lastName);
        entity.setEmploymentType(employmentType);

        entity.setServices(services);
        entity.setRegions(regions);
        entity.setPools(pools);
        entity.setRestrictions(restrictions);
        entity.setCustomerExperience(customerExperience);

        ConsultantEntity saved = consultantRepository.save(entity);
        return consultantMapper.toDto(saved);
    }

    @Tool(
            name = "consultant_get_by_id",
            description = "Retrieve a consultant by consultantId."
    )
    @Transactional(readOnly = true)
    public ConsultantDTO getById(String consultantId) {

        String id = ValidationUtils.requireNonBlank(consultantId, "consultantId");

        ConsultantEntity entity = consultantRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Consultant hittades inte: " + id));

        return consultantMapper.toDto(entity);
    }

    @Tool(
            name = "consultant_find_by_name",
            description = "Find consultants by firstName and lastName (case-insensitive). Returns matching consultants."
    )
    @Transactional(readOnly = true)
    public List<ConsultantDTO> findByName(String firstName, String lastName) {
        String fn = ValidationUtils.requireNonBlank(firstName, "firstName");
        String ln = ValidationUtils.requireNonBlank(lastName, "lastName");

        return consultantRepository.findByFirstNameIgnoreCaseAndLastNameIgnoreCase(fn, ln)
                .stream()
                .map(consultantMapper::toDto)
                .toList();
    }

    @Tool(
            name = "consultant_available_by_date",
            description = "Returns consultants who are AVAILABLE on the specified date."
    )
    @Transactional(readOnly = true)
    public List<ConsultantDTO> findAvailableByDate(java.time.LocalDate date) {

        ValidationUtils.requireNonNull(date, "date");

        return availabilityRepository
                .findDistinctConsultantsByDateAndStatus(date, AvailabilityStatus.AVAILABLE)
                .stream()
                .map(consultantMapper::toDto)
                .toList();
    }

    @Tool(
            name = "consultant_available_by_datetime_range",
            description = "Returns consultants who have an AVAILABLE slot that fully covers the given time range on the specified date."
    )
    @Transactional(readOnly = true)
    public List<ConsultantDTO> findAvailableByDateTimeRange(LocalDate date, LocalTime startTime, LocalTime endTime) {
        ValidationUtils.requireNonNull(date, "date");
        ValidationUtils.requireNonNull(startTime, "startTime");
        ValidationUtils.requireNonNull(endTime, "endTime");
        ValidationUtils.requireTimeRange(startTime, endTime, "startTime", "endTime");

        List<String> ids = availabilityRepository
                .findByDateAndStatusAndStartTimeLessThanEqualAndEndTimeGreaterThanEqual(
                        date, AvailabilityStatus.AVAILABLE, startTime, endTime
                )
                .stream()
                .map(a -> a.getConsultant().getConsultantId())
                .distinct()
                .toList();

        return consultantRepository.findAllById(ids).stream()
                .map(consultantMapper::toDto)
                .toList();
    }

    // -------------------- helpers --------------------
    private static String cleanToken(String s) {
        if (s == null) return null;
        String t = s.trim();

        // remove surrounding quotes if present
        if ((t.startsWith("\"") && t.endsWith("\"")) || (t.startsWith("'") && t.endsWith("'"))) {
            t = t.substring(1, t.length() - 1).trim();
        }

        // remove trailing punctuation like ?, . , :
        t = t.replaceAll("[\\p{Punct}]+$", "");

        return t.trim();
    }

    private List<String> splitServices(String services) {
        if (services == null || services.isBlank()) return List.of();

        return java.util.Arrays.stream(services.split(";"))
                .map(String::trim)
                .map(ConsultantService::cleanToken)
                .filter(s -> s != null && !s.isBlank())
                .toList();
    }

    @Tool(
            name = "consultant_get_services_by_id",
            description = "Returns the services for the specified consultantId as a list of strings."
    )
    @Transactional(readOnly = true)
    public List<String> getServicesById(String consultantId) {

        String id = ValidationUtils.requireNonBlank(consultantId, "consultantId");

        ConsultantEntity entity = consultantRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Consultant not found: " + id));

        return splitServices(entity.getServices());
    }

    @Tool(
            name = "consultant_get_services_by_name",
            description = "Returns the services for a consultant identified by firstName and lastName as a list of strings."
    )
    @Transactional(readOnly = true)
    public List<String> getServicesByName(String firstName, String lastName) {

        String fn = ValidationUtils.requireNonBlank(firstName, "firstName");
        String ln = ValidationUtils.requireNonBlank(lastName, "lastName");

        List<ConsultantEntity> matches =
                consultantRepository.findByFirstNameIgnoreCaseAndLastNameIgnoreCase(fn, ln);

        if (matches.isEmpty()) {
            throw new NotFoundException("Consultant not found: " + fn + " " + ln);
        }
        if (matches.size() > 1) {
            throw new BadRequestException("Multiple consultants found with name: " + fn + " " + ln);
        }

        return splitServices(matches.get(0).getServices());
    }

    @Tool(
            name = "service_find_consultants_by_services",
            description = "Find consultants by multiple services. matchMode=ALL requires all services; matchMode=ANY requires at least one."
    )
    @Transactional(readOnly = true)
    public List<ConsultantDTO> findConsultantsByServices(List<String> services, ServiceMatchMode matchMode) {
        ValidationUtils.requireNonNull(services, "services");
        if (services.isEmpty()) throw new BadRequestException("services must not be empty");
        ValidationUtils.requireNonNull(matchMode, "matchMode");

        List<String> needles = services.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .map(String::toLowerCase)
                .distinct()
                .toList();

        if (needles.isEmpty()) throw new BadRequestException("services must not be empty");

        return consultantRepository.findAll().stream()
                .filter(c -> {
                    List<String> hay = splitServices(c.getServices()).stream()
                            .map(String::toLowerCase)
                            .toList();

                    return switch (matchMode) {
                        case ANY -> hay.stream().anyMatch(needles::contains);
                        case ALL -> hay.containsAll(needles);
                    };
                })
                .map(consultantMapper::toDto)
                .toList();
    }

    @Tool(
            name = "consultant_list",
            description = "List all consultants."
    )
    @Transactional(readOnly = true)
    public List<ConsultantDTO> list() {
        return consultantRepository.findAll()
                .stream()
                .map(consultantMapper::toDto)
                //.limit(5)
                .toList();
    }

    /**
     * Patch-update: updates only fields that are non-null in patch.
     */
    @Tool(
            name = "consultant_update",
            description = "Patch-update an existing consultant. Only non-null fields in the patch are applied; CSV fields are validated to not contain empty items."
    )
    public ConsultantDTO update(String consultantId, ConsultantDTO patch) {

        String id = ValidationUtils.requireNonBlank(consultantId, "consultantId");
        ValidationUtils.requireNonNull(patch, "consultant");

        ConsultantEntity existing = consultantRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Consultant hittades inte: " + id));

        if (patch.firstName() != null) {
            existing.setFirstName(ValidationUtils.requireNonBlank(patch.firstName(), "firstName"));
        }
        if (patch.lastName() != null) {
            existing.setLastName(ValidationUtils.requireNonBlank(patch.lastName(), "lastName"));
        }
        if (patch.employmentType() != null) {
            existing.setEmploymentType(ValidationUtils.requireNonBlank(patch.employmentType(), "employmentType"));
        }

        if (patch.services() != null) {
            String v = ValidationUtils.trimToNull(patch.services());
            ValidationUtils.requireCsvNoEmptyItems(v, "services");
            existing.setServices(v);
        }
        if (patch.regions() != null) {
            String v = ValidationUtils.trimToNull(patch.regions());
            ValidationUtils.requireCsvNoEmptyItems(v, "regions");
            existing.setRegions(v);
        }
        if (patch.pools() != null) {
            String v = ValidationUtils.trimToNull(patch.pools());
            ValidationUtils.requireCsvNoEmptyItems(v, "pools");
            existing.setPools(v);
        }

        if (patch.restrictions() != null) {
            existing.setRestrictions(ValidationUtils.trimToNull(patch.restrictions()));
        }
        if (patch.customerExperience() != null) {
            existing.setCustomerExperience(ValidationUtils.trimToNull(patch.customerExperience()));
        }

        ConsultantEntity saved = consultantRepository.save(existing);
        return consultantMapper.toDto(saved);
    }

    @Tool(
            name = "consultant_delete",
            description = "Delete a consultant by consultantId."
    )
    public void delete(String consultantId) {

        String id = ValidationUtils.requireNonBlank(consultantId, "consultantId");

        if (!consultantRepository.existsById(id)) {
            throw new NotFoundException("Consultant hittades inte: " + id);
        }

        consultantRepository.deleteById(id);
    }
}