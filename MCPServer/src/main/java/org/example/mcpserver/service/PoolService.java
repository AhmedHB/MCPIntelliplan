package org.example.mcpserver.service;

import org.example.mcpserver.dto.PoolDTO;
import org.example.mcpserver.repository.PoolRepository;
import org.example.mcpserver.repository.domain.PoolEntity;
import org.example.mcpserver.service.exception.BadRequestException;
import org.example.mcpserver.service.exception.NotFoundException;
import org.example.mcpserver.service.mapping.PoolMapper;
import org.example.mcpserver.service.validation.ValidationUtils;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class PoolService {

    private final PoolRepository poolRepository;
    private final PoolMapper poolMapper;

    public PoolService(PoolRepository poolRepository,
                       PoolMapper poolMapper) {
        this.poolRepository = poolRepository;
        this.poolMapper = poolMapper;
    }

    @Tool(
            name = "pool_create",
            description = "Create a pool with validation (required: poolId, description). Optional regions must be a valid CSV without empty items."
    )
    public PoolDTO create(PoolDTO dto) {
        ValidationUtils.requireNonNull(dto, "pool");

        String id = ValidationUtils.requireNonBlank(dto.poolId(), "poolId");
        if (poolRepository.existsById(id)) {
            throw new BadRequestException("Pool finns redan: " + id);
        }

        String description = ValidationUtils.requireNonBlank(dto.description(), "description");

        String regions = null;
        if (dto.regions() != null) {
            regions = ValidationUtils.requireNonBlank(dto.regions(), "regions");
            ValidationUtils.requireCsvNoEmptyItems(regions, "regions");
        }

        PoolEntity entity = poolMapper.toEntity(dto);
        entity.setPoolId(id);
        entity.setDescription(description);
        entity.setRegions(regions);

        PoolEntity saved = poolRepository.save(entity);
        return poolMapper.toDto(saved);
    }

    @Tool(
            name = "pool_get_by_id",
            description = "Retrieve a pool by poolId."
    )
    @Transactional(readOnly = true)
    public PoolDTO getById(String poolId) {

        String id = ValidationUtils.requireNonBlank(poolId, "poolId");

        PoolEntity entity = poolRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Pool hittades inte: " + id));

        return poolMapper.toDto(entity);
    }

    @Tool(
            name = "pool_list",
            description = "List all pools."
    )
    @Transactional(readOnly = true)
    public List<PoolDTO> list() {
        return poolRepository.findAll()
                .stream()
                .map(poolMapper::toDto)
                .toList();
    }

    /**
     * Patch-update: updates only fields that are non-null in patch.
     */
    @Tool(
            name = "pool_update",
            description = "Patch-update a pool. Only non-null fields are applied. regions must be a valid CSV without empty items."
    )
    public PoolDTO update(String poolId, PoolDTO patch) {

        String id = ValidationUtils.requireNonBlank(poolId, "poolId");
        ValidationUtils.requireNonNull(patch, "pool");

        PoolEntity existing = poolRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Pool hittades inte: " + id));

        if (patch.description() != null) {
            existing.setDescription(ValidationUtils.requireNonBlank(patch.description(), "description"));
        }

        if (patch.regions() != null) {
            String regions = ValidationUtils.requireNonBlank(patch.regions(), "regions");
            ValidationUtils.requireCsvNoEmptyItems(regions, "regions");
            existing.setRegions(regions);
        }

        PoolEntity saved = poolRepository.save(existing);
        return poolMapper.toDto(saved);
    }

    @Tool(
            name = "pool_delete",
            description = "Delete a pool by poolId."
    )
    public void delete(String poolId) {

        String id = ValidationUtils.requireNonBlank(poolId, "poolId");

        if (!poolRepository.existsById(id)) {
            throw new NotFoundException("Pool hittades inte: " + id);
        }

        poolRepository.deleteById(id);
    }
}