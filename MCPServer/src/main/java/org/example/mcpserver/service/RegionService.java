package org.example.mcpserver.service;

import org.example.mcpserver.dto.RegionDTO;
import org.example.mcpserver.repository.RegionRepository;
import org.example.mcpserver.repository.domain.RegionEntity;
import org.example.mcpserver.service.exception.BadRequestException;
import org.example.mcpserver.service.exception.NotFoundException;
import org.example.mcpserver.service.mapping.RegionMapper;
import org.example.mcpserver.service.validation.ValidationUtils;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class RegionService {

    private final RegionRepository regionRepository;
    private final RegionMapper regionMapper;

    public RegionService(RegionRepository regionRepository,
                         RegionMapper regionMapper) {
        this.regionRepository = regionRepository;
        this.regionMapper = regionMapper;
    }

    @Tool(
            name = "region_create",
            description = "Create a region with validation (required: regionCode, name). regionCode must be unique."
    )
    public RegionDTO create(RegionDTO dto) {

        ValidationUtils.requireNonNull(dto, "region");

        String regionCode = ValidationUtils.requireNonBlank(dto.regionCode(), "regionCode");
        String name = ValidationUtils.requireNonBlank(dto.name(), "name");

        if (regionRepository.existsById(regionCode)) {
            throw new BadRequestException("Region finns redan: " + regionCode);
        }

        RegionEntity entity = regionMapper.toEntity(dto);
        entity.setRegionCode(regionCode);
        entity.setName(name);

        RegionEntity saved = regionRepository.save(entity);
        return regionMapper.toDto(saved);
    }

    @Tool(
            name = "region_get_by_id",
            description = "Retrieve a region by regionCode."
    )
    @Transactional(readOnly = true)
    public RegionDTO getById(String regionCode) {

        String id = ValidationUtils.requireNonBlank(regionCode, "regionCode");

        RegionEntity entity = regionRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Region hittades inte: " + id));

        return regionMapper.toDto(entity);
    }

    @Tool(
            name = "organization_list_regions",
            description = "Lists all available regions."
    )
    @Transactional(readOnly = true)
    public List<RegionDTO> listRegions() {

        return regionRepository.findAll().stream()
                .sorted(java.util.Comparator.comparing(RegionEntity::getRegionCode))
                .map(r -> new RegionDTO(
                        r.getRegionCode(),
                        r.getName()
                ))
                .toList();
    }

    @Tool(
            name = "region_list",
            description = "List all regions."
    )
    @Transactional(readOnly = true)
    public List<RegionDTO> list() {
        return regionRepository.findAll()
                .stream()
                .map(regionMapper::toDto)
                .toList();
    }

    @Tool(
            name = "region_update",
            description = "Patch-update a region. Only non-null fields are applied (currently: name)."
    )
    public RegionDTO update(String regionCode, RegionDTO patch) {

        String id = ValidationUtils.requireNonBlank(regionCode, "regionCode");
        ValidationUtils.requireNonNull(patch, "region");

        RegionEntity existing = regionRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Region hittades inte: " + id));

        if (patch.name() != null) {
            existing.setName(ValidationUtils.requireNonBlank(patch.name(), "name"));
        }

        RegionEntity saved = regionRepository.save(existing);
        return regionMapper.toDto(saved);
    }

    @Tool(
            name = "region_delete",
            description = "Delete a region by regionCode."
    )
    public void delete(String regionCode) {

        String id = ValidationUtils.requireNonBlank(regionCode, "regionCode");

        if (!regionRepository.existsById(id)) {
            throw new NotFoundException("Region hittades inte: " + id);
        }

        regionRepository.deleteById(id);
    }
}