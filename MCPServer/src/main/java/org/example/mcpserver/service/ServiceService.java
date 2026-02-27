package org.example.mcpserver.service;

import org.example.mcpserver.dto.ServiceDTO;
import org.example.mcpserver.repository.ServiceRepository;
import org.example.mcpserver.repository.domain.ServiceEntity;
import org.example.mcpserver.service.exception.BadRequestException;
import org.example.mcpserver.service.exception.NotFoundException;
import org.example.mcpserver.service.mapping.ServiceMapper;
import org.example.mcpserver.service.validation.ValidationUtils;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class ServiceService {

    private final ServiceRepository serviceRepository;
    private final ServiceMapper serviceMapper;

    public ServiceService(ServiceRepository serviceRepository,
                          ServiceMapper serviceMapper) {
        this.serviceRepository = serviceRepository;
        this.serviceMapper = serviceMapper;
    }

    @Tool(
            name = "service_create",
            description = "Create a service definition (required: serviceCode, description). serviceCode must be unique."
    )
    public ServiceDTO create(ServiceDTO dto) {

        ValidationUtils.requireNonNull(dto, "service");

        String code = ValidationUtils.requireNonBlank(dto.serviceCode(), "serviceCode");

        if (serviceRepository.existsById(code)) {
            throw new BadRequestException("Service finns redan: " + code);
        }

        String description =
                ValidationUtils.requireNonBlank(dto.description(), "description");

        ServiceEntity entity = serviceMapper.toEntity(dto);
        entity.setServiceCode(code);
        entity.setDescription(description);

        ServiceEntity saved = serviceRepository.save(entity);

        return serviceMapper.toDto(saved);
    }

    @Tool(
            name = "service_get_by_id",
            description = "Retrieve a service definition by serviceCode."
    )
    @Transactional(readOnly = true)
    public ServiceDTO getById(String serviceCode) {

        String code = ValidationUtils.requireNonBlank(serviceCode, "serviceCode");

        ServiceEntity entity = serviceRepository.findById(code)
                .orElseThrow(() ->
                        new NotFoundException("Service hittades inte: " + code));

        return serviceMapper.toDto(entity);
    }

    @Tool(
            name = "service_list",
            description = "List all service definitions."
    )
    @Transactional(readOnly = true)
    public List<ServiceDTO> list() {
        return serviceRepository.findAll()
                .stream()
                .map(serviceMapper::toDto)
                .toList();
    }

    @Tool(
            name = "service_update",
            description = "Patch-update a service definition. Only non-null fields are applied (currently: description)."
    )
    public ServiceDTO update(String serviceCode, ServiceDTO patch) {

        String code = ValidationUtils.requireNonBlank(serviceCode, "serviceCode");
        ValidationUtils.requireNonNull(patch, "service");

        ServiceEntity existing = serviceRepository.findById(code)
                .orElseThrow(() ->
                        new NotFoundException("Service hittades inte: " + code));

        if (patch.description() != null) {
            existing.setDescription(
                    ValidationUtils.requireNonBlank(patch.description(), "description")
            );
        }

        ServiceEntity saved = serviceRepository.save(existing);

        return serviceMapper.toDto(saved);
    }

    @Tool(
            name = "service_delete",
            description = "Delete a service definition by serviceCode."
    )
    public void delete(String serviceCode) {

        String code = ValidationUtils.requireNonBlank(serviceCode, "serviceCode");

        if (!serviceRepository.existsById(code)) {
            throw new NotFoundException("Service hittades inte: " + code);
        }

        serviceRepository.deleteById(code);
    }
}