package org.example.mcpserver.repository;

import org.example.mcpserver.repository.domain.CustomerEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CustomerRepository extends JpaRepository<CustomerEntity, String> {
    List<CustomerEntity> findByCustomerNameContainingIgnoreCase(String customerName);

    List<CustomerEntity> findByRegionIgnoreCase(String region);

    List<CustomerEntity> findByRiskProfileIgnoreCase(String riskProfile);

    List<CustomerEntity> findByRegionIgnoreCaseAndRiskProfileIgnoreCase(String region, String riskProfile);

    List<CustomerEntity> findByCustomerNameContainingIgnoreCaseAndRegionIgnoreCase(String customerName, String region);

    List<CustomerEntity> findByCustomerNameContainingIgnoreCaseAndRiskProfileIgnoreCase(String customerName, String riskProfile);

    List<CustomerEntity> findByCustomerNameContainingIgnoreCaseAndRegionIgnoreCaseAndRiskProfileIgnoreCase(
            String customerName, String region, String riskProfile
    );
}
