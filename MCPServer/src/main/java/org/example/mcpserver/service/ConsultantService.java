package org.example.mcpserver.service;

import org.example.mcpserver.dto.ConsultantCountByRegionDTO;
import org.example.mcpserver.dto.ConsultantDTO;
import org.example.mcpserver.dto.RegionByConsultantResponseDTO;
import org.example.mcpserver.repository.AvailabilityRepository;
import org.example.mcpserver.repository.ConsultantRepository;
import org.example.mcpserver.repository.RegionRepository;
import org.example.mcpserver.repository.domain.AvailabilityEntity;
import org.example.mcpserver.repository.domain.AvailabilityStatus;
import org.example.mcpserver.repository.domain.ConsultantEntity;
import org.example.mcpserver.repository.domain.RegionEntity;
import org.example.mcpserver.service.exception.BadRequestException;
import org.example.mcpserver.service.exception.NotFoundException;
import org.example.mcpserver.service.mapping.ConsultantMapper;
import org.example.mcpserver.service.validation.ValidationUtils;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
@Transactional
public class ConsultantService {

    private final ConsultantRepository consultantRepository;
    private final AvailabilityRepository availabilityRepository;
    private final RegionRepository regionRepository;
    private final ConsultantMapper consultantMapper;

    public ConsultantService(ConsultantRepository consultantRepository,
                             AvailabilityRepository availabilityRepository, PoolService poolService, RegionRepository regionRepository,
                             ConsultantMapper consultantMapper) {
        this.consultantRepository = consultantRepository;
        this.availabilityRepository = availabilityRepository;
        this.regionRepository = regionRepository;
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
            name = "organization_get_region_by_consultant_id",
            description = "Returns the region(s) for a consultant by consultantId (e.g. CONS_100086). Uses the consultant.regions field."
    )
    @Transactional(readOnly = true)
    public RegionByConsultantResponseDTO getRegionByConsultantId(String consultantId) {

        String id = ValidationUtils.requireNonBlank(consultantId, "consultantId");

        ConsultantEntity entity = consultantRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Consultant hittades inte: " + id));

        String regions = ValidationUtils.trimToNull(entity.getRegions());
        if (regions == null) {
            throw new NotFoundException("No region set for consultant: " + id);
        }

        return new RegionByConsultantResponseDTO(
                entity.getConsultantId(),
                entity.getFirstName(),
                entity.getLastName(),
                regions
        );
    }

    @Tool(
            name = "organization_get_region_by_consultant_name",
            description = "Returns the region(s) for a consultant identified by firstName and lastName (case-insensitive). Uses the consultant.regions field."
    )
    @Transactional(readOnly = true)
    public RegionByConsultantResponseDTO getRegionByConsultantName(String firstName, String lastName) {

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

        ConsultantEntity entity = matches.get(0);

        String regions = ValidationUtils.trimToNull(entity.getRegions());
        if (regions == null) {
            throw new NotFoundException("No region set for consultant: " + entity.getConsultantId());
        }

        return new RegionByConsultantResponseDTO(
                entity.getConsultantId(),
                entity.getFirstName(),
                entity.getLastName(),
                regions
        );
    }

    @Tool(
            name = "organization_list_consultants_by_region",
            description = "Lists consultants in a given region. Input can be region code (e.g. SE-STH) or region name (e.g. Stockholm). Matches against consultant.regions (semicolon-separated codes)."
    )
    @Transactional(readOnly = true)
    public List<ConsultantDTO> listConsultantsByRegion(String region) {

        String regionCode = resolveRegionCode(region);

        return consultantRepository.findAll().stream()
                .filter(c -> hasRegionToken(c.getRegions(), regionCode))
                .map(consultantMapper::toDto)
                .toList();
    }

    // -------------------- helpers --------------------

    private String resolveRegionCode(String input) {
        String candidate = cleanToken(input);

        // if user already provides SE-XXX, try PK lookup
        if (candidate != null && candidate.toUpperCase(Locale.ROOT).startsWith("SE-")) {
            String code = candidate.toUpperCase(Locale.ROOT);
            // verify it exists; otherwise fail early
            regionRepository.findById(code)
                    .orElseThrow(() -> new NotFoundException("Region not found: " + code));
            return code;
        }

        // otherwise treat as region name, e.g. "Stockholm"
        return regionRepository.findByNameIgnoreCase(candidate)
                .map(r -> r.getRegionCode())
                .orElseThrow(() -> new NotFoundException("Region not found: " + candidate));
    }
    private boolean hasRegionToken(String regionsCsv, String regionCode) {
        String regions = ValidationUtils.trimToNull(regionsCsv);
        if (regions == null) return false;

        return Arrays.stream(regions.split(";"))
                .map(String::trim)
                .map(ConsultantService::cleanToken)
                .filter(s -> s != null && !s.isBlank())
                .anyMatch(tok -> tok.equalsIgnoreCase(regionCode));
    }

    @Tool(
            name = "organization_count_consultants_by_region",
            description = "Counts how many consultants belong to a given region. Input can be region code (e.g. SE-LIN) or region name (e.g. Linköping). Uses consultant.regions (semicolon-separated codes)."
    )
    @Transactional(readOnly = true)
    public ConsultantCountByRegionDTO countConsultantsByRegion(String region) {

        String regionCode = resolveRegionCode(region);

        RegionEntity regionEntity = regionRepository.findById(regionCode)
                .orElseThrow(() -> new NotFoundException("Region not found: " + regionCode));

        long count = consultantRepository.findAll().stream()
                .filter(c -> hasRegionToken(c.getRegions(), regionCode))
                .count();

        return new ConsultantCountByRegionDTO(
                regionCode,
                regionEntity.getName(),
                count
        );
    }

    @Tool(
            name = "organization_list_regions_with_consultant_counts",
            description = "Lists all regions with consultant counts. Counts are based on consultant.regions (semicolon-separated region codes)."
    )
    @Transactional(readOnly = true)
    public List<ConsultantCountByRegionDTO> listRegionsWithConsultantCounts() {

        // 1) preload all regions (source of truth for name + code)
        List<RegionEntity> regions = regionRepository.findAll();

        // 2) init counters per regionCode
        java.util.Map<String, Long> counts = new java.util.HashMap<>();
        for (RegionEntity r : regions) {
            counts.put(r.getRegionCode(), 0L); // adjust getter if needed
        }

        // 3) count: each consultant contributes +1 to each region token they have
        for (ConsultantEntity c : consultantRepository.findAll()) {
            String csv = ValidationUtils.trimToNull(c.getRegions());
            if (csv == null) continue;

            java.util.Set<String> uniqueTokens = java.util.Arrays.stream(csv.split(";"))
                    .map(String::trim)
                    .map(ConsultantService::cleanToken)
                    .filter(s -> s != null && !s.isBlank())
                    .map(s -> s.toUpperCase(Locale.ROOT))
                    .collect(java.util.stream.Collectors.toSet());

            for (String code : uniqueTokens) {
                if (counts.containsKey(code)) {
                    counts.put(code, counts.get(code) + 1);
                }
            }
        }

        // 4) map to DTO list (sorted by regionCode)
        return regions.stream()
                .map(r -> new ConsultantCountByRegionDTO(
                        r.getRegionCode(),
                        r.getName(),
                        counts.getOrDefault(r.getRegionCode(), 0L)
                ))
                .sorted(java.util.Comparator.comparing(ConsultantCountByRegionDTO::regionCode))
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