package org.example.mcpserver.service.mapping;

import org.example.mcpserver.dto.AssignmentDTO;
import org.example.mcpserver.repository.domain.AssignmentEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = EntityRefMapper.class)
public interface AssignmentMapper {

    @Mapping(target = "customerId", source = "customer", qualifiedByName = "customerToId")
    @Mapping(target = "consultantId", source = "consultant", qualifiedByName = "consultantToId")
    AssignmentDTO toDto(AssignmentEntity entity);

    @Mapping(target = "customer", source = "customerId", qualifiedByName = "idToCustomer")
    @Mapping(target = "consultant", source = "consultantId", qualifiedByName = "idToConsultant")
    AssignmentEntity toEntity(AssignmentDTO dto);
}
