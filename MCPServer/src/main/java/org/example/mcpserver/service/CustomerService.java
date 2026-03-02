package org.example.mcpserver.service;

import org.example.mcpserver.dto.CustomerDTO;
import org.example.mcpserver.repository.CustomerRepository;
import org.example.mcpserver.repository.domain.CustomerEntity;
import org.example.mcpserver.service.exception.BadRequestException;
import org.example.mcpserver.service.exception.NotFoundException;
import org.example.mcpserver.service.mapping.CustomerMapper;
import org.example.mcpserver.service.validation.ValidationUtils;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
@Transactional
public class CustomerService {

    private static final Set<String> ALLOWED_RISK_PROFILES =
            Set.of("LOW", "MEDIUM", "HIGH");

    private final CustomerRepository customerRepository;
    private final CustomerMapper customerMapper;

    public CustomerService(CustomerRepository customerRepository,
                           CustomerMapper customerMapper) {
        this.customerRepository = customerRepository;
        this.customerMapper = customerMapper;
    }

    @Tool(
            name = "customer_create",
            description = "Create a customer with validation (required: customerId, customerName, region, requiredServices, riskProfile). riskProfile must be one of LOW/MEDIUM/HIGH."
    )
    public CustomerDTO create(CustomerDTO dto) {
        ValidationUtils.requireNonNull(dto, "customer");

        String id = ValidationUtils.requireNonBlank(dto.customerId(), "customerId");
        if (customerRepository.existsById(id)) {
            throw new BadRequestException("Customer finns redan: " + id);
        }

        String customerName = ValidationUtils.requireNonBlank(dto.customerName(), "customerName");
        String region = ValidationUtils.requireNonBlank(dto.region(), "region");
        String requiredServices = ValidationUtils.requireNonBlank(dto.requiredServices(), "requiredServices");
        String riskProfile = ValidationUtils.requireNonBlank(dto.riskProfile(), "riskProfile").toUpperCase();

        // requiredServices: CSV without empty items
        ValidationUtils.requireCsvNoEmptyItems(requiredServices, "requiredServices");

        // riskProfile: must be allowed
        if (!ALLOWED_RISK_PROFILES.contains(riskProfile)) {
            throw new BadRequestException("riskProfile måste vara en av: " + ALLOWED_RISK_PROFILES);
        }

        CustomerEntity entity = customerMapper.toEntity(dto);
        entity.setCustomerId(id);
        entity.setCustomerName(customerName);
        entity.setRegion(region);
        entity.setRequiredServices(requiredServices);
        entity.setRiskProfile(riskProfile);

        CustomerEntity saved = customerRepository.save(entity);
        return customerMapper.toDto(saved);
    }

    @Tool(
            name = "customer_get_by_id",
            description = "Retrieve a customer by customerId."
    )
    @Transactional(readOnly = true)
    public CustomerDTO getById(String customerId) {

        String id = ValidationUtils.requireNonBlank(customerId, "customerId");

        CustomerEntity entity = customerRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Customer hittades inte: " + id));

        return customerMapper.toDto(entity);
    }

    @Tool(
            name = "customer_search",
            description = "Search customers by optional filters: customerName (contains, case-insensitive), region (exact, case-insensitive), riskProfile (LOW|MEDIUM|HIGH). Any parameter may be null."
    )
    @Transactional(readOnly = true)
    public List<CustomerDTO> search(String customerName, String region, String riskProfile) {

        String name = ValidationUtils.trimToNull(customerName);
        String reg = ValidationUtils.trimToNull(region);
        String rp = ValidationUtils.trimToNull(riskProfile);
        if (rp != null) rp = rp.toUpperCase();

        if (rp != null && !ALLOWED_RISK_PROFILES.contains(rp)) {
            throw new BadRequestException("riskProfile måste vara en av: " + ALLOWED_RISK_PROFILES);
        }

        List<CustomerEntity> result;

        if (name != null && reg != null && rp != null) {
            result = customerRepository
                    .findByCustomerNameContainingIgnoreCaseAndRegionIgnoreCaseAndRiskProfileIgnoreCase(name, reg, rp);
        } else if (name != null && reg != null) {
            result = customerRepository
                    .findByCustomerNameContainingIgnoreCaseAndRegionIgnoreCase(name, reg);
        } else if (name != null && rp != null) {
            result = customerRepository
                    .findByCustomerNameContainingIgnoreCaseAndRiskProfileIgnoreCase(name, rp);
        } else if (reg != null && rp != null) {
            result = customerRepository
                    .findByRegionIgnoreCaseAndRiskProfileIgnoreCase(reg, rp);
        } else if (name != null) {
            result = customerRepository
                    .findByCustomerNameContainingIgnoreCase(name);
        } else if (reg != null) {
            result = customerRepository
                    .findByRegionIgnoreCase(reg);
        } else if (rp != null) {
            result = customerRepository
                    .findByRiskProfileIgnoreCase(rp);
        } else {
            result = customerRepository.findAll();
        }

        return result.stream()
                .map(customerMapper::toDto)
                .toList();
    }

    @Tool(
            name = "customer_list",
            description = "List all customers."
    )
    @Transactional(readOnly = true)
    public List<CustomerDTO> list() {
        return customerRepository.findAll()
                .stream()
                .map(customerMapper::toDto)
                .toList();
    }

    /**
     * Patch-update: updates only fields that are non-null in patch.
     */
    @Tool(
            name = "customer_update",
            description = "Patch-update an existing customer. Only non-null fields are applied. riskProfile must be one of LOW/MEDIUM/HIGH; requiredServices CSV cannot contain empty items."
    )
    public CustomerDTO update(String customerId, CustomerDTO patch) {

        String id = ValidationUtils.requireNonBlank(customerId, "customerId");
        ValidationUtils.requireNonNull(patch, "customer");

        CustomerEntity existing = customerRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Customer hittades inte: " + id));

        if (patch.customerName() != null) {
            existing.setCustomerName(ValidationUtils.requireNonBlank(patch.customerName(), "customerName"));
        }

        if (patch.region() != null) {
            existing.setRegion(ValidationUtils.requireNonBlank(patch.region(), "region"));
        }

        if (patch.requiredServices() != null) {
            String v = ValidationUtils.requireNonBlank(patch.requiredServices(), "requiredServices");
            ValidationUtils.requireCsvNoEmptyItems(v, "requiredServices");
            existing.setRequiredServices(v);
        }

        if (patch.riskProfile() != null) {
            String rp = ValidationUtils.requireNonBlank(patch.riskProfile(), "riskProfile").toUpperCase();
            if (!ALLOWED_RISK_PROFILES.contains(rp)) {
                throw new BadRequestException("riskProfile måste vara en av: " + ALLOWED_RISK_PROFILES);
            }
            existing.setRiskProfile(rp);
        }

        CustomerEntity saved = customerRepository.save(existing);
        return customerMapper.toDto(saved);
    }

    @Tool(
            name = "customer_delete",
            description = "Delete a customer by customerId."
    )
    public void delete(String customerId) {

        String id = ValidationUtils.requireNonBlank(customerId, "customerId");

        if (!customerRepository.existsById(id)) {
            throw new NotFoundException("Customer hittades inte: " + id);
        }

        customerRepository.deleteById(id);
    }
}