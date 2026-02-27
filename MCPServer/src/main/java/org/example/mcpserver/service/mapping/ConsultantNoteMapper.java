package org.example.mcpserver.service.mapping;

import org.example.mcpserver.dto.ConsultantNoteDTO;
import org.example.mcpserver.repository.domain.ConsultantNoteEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = EntityRefMapper.class)
public interface ConsultantNoteMapper {

    @Mapping(target = "consultantId", source = "consultant", qualifiedByName = "consultantToId")
    @Mapping(target = "customerId", source = "customer", qualifiedByName = "customerToId")
    @Mapping(target = "assignmentId", source = "assignment", qualifiedByName = "assignmentToId")
    ConsultantNoteDTO toDto(ConsultantNoteEntity entity);

    @Mapping(target = "consultant", source = "consultantId", qualifiedByName = "idToConsultant")
    @Mapping(target = "customer", source = "customerId", qualifiedByName = "idToCustomer")
    @Mapping(target = "assignment", source = "assignmentId", qualifiedByName = "idToAssignment")
    ConsultantNoteEntity toEntity(ConsultantNoteDTO dto);
}