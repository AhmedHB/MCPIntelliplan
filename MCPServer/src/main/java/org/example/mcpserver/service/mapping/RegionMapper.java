package org.example.mcpserver.service.mapping;

import org.example.mcpserver.dto.RegionDTO;
import org.example.mcpserver.repository.domain.RegionEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface RegionMapper {
    RegionDTO toDto(RegionEntity entity);
    RegionEntity toEntity(RegionDTO dto);
}
