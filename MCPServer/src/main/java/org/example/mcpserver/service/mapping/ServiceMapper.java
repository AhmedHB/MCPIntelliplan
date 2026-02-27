package org.example.mcpserver.service.mapping;

import org.example.mcpserver.dto.ServiceDTO;
import org.example.mcpserver.repository.domain.ServiceEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ServiceMapper {
    ServiceDTO toDto(ServiceEntity entity);
    ServiceEntity toEntity(ServiceDTO dto);
}
