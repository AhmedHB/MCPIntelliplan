package org.example.mcpserver.service.mapping;

import org.example.mcpserver.dto.AvailabilityDTO;
import org.example.mcpserver.repository.domain.AvailabilityEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = EntityRefMapper.class)
public interface AvailabilityMapper {

    @Mapping(target = "consultantId", source = "consultant", qualifiedByName = "consultantToId")
    AvailabilityDTO toDto(AvailabilityEntity entity);

    @Mapping(target = "consultant", source = "consultantId", qualifiedByName = "idToConsultant")
    AvailabilityEntity toEntity(AvailabilityDTO dto);
}