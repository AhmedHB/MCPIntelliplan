package org.example.mcpserver.service.mapping;

import org.example.mcpserver.dto.PoolDTO;
import org.example.mcpserver.repository.domain.PoolEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface PoolMapper {
    PoolDTO toDto(PoolEntity entity);
    PoolEntity toEntity(PoolDTO dto);
}
