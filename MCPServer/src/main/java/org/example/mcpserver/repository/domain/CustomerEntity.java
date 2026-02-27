package org.example.mcpserver.repository.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "customers")
public class CustomerEntity {

    @Id
    @Column(name = "customer_id", nullable = false)
    private String customerId;

    @Column(name = "customer_name", nullable = false)
    private String customerName;

    @Column(name = "region", nullable = false)
    private String region;

    @Column(name = "required_services", nullable = false)
    private String requiredServices;

    @Column(name = "risk_profile", nullable = false)
    private String riskProfile;

    public CustomerEntity() {}

    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }

    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }

    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }

    public String getRequiredServices() { return requiredServices; }
    public void setRequiredServices(String requiredServices) { this.requiredServices = requiredServices; }

    public String getRiskProfile() { return riskProfile; }
    public void setRiskProfile(String riskProfile) { this.riskProfile = riskProfile; }
}
