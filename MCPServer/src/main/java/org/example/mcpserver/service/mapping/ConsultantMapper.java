package org.example.mcpserver.service.mapping;

import org.example.mcpserver.dto.ConsultantDTO;
import org.example.mcpserver.repository.domain.ConsultantEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ConsultantMapper {
    ConsultantDTO toDto(ConsultantEntity entity);
    ConsultantEntity toEntity(ConsultantDTO dto);
}
