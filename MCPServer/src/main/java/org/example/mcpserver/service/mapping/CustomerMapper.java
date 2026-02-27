package org.example.mcpserver.service.mapping;

import org.example.mcpserver.dto.CustomerDTO;
import org.example.mcpserver.repository.domain.CustomerEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface CustomerMapper {
    CustomerDTO toDto(CustomerEntity entity);
    CustomerEntity toEntity(CustomerDTO dto);
}
