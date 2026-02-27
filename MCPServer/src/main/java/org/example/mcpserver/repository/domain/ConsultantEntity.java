package org.example.mcpserver.repository.domain;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Entity
@Table(name = "consultants")
public class ConsultantEntity {

    @Id
    @Column(name = "consultant_id", nullable = false)
    private String consultantId;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(name = "employment_type", nullable = false)
    private String employmentType;

    @Column(name = "services")
    private String services;

    @Column(name = "regions")
    private String regions;

    @Column(name = "pools")
    private String pools;

    @Column(name = "restrictions")
    private String restrictions;

    @Column(name = "customer_experience")
    private String customerExperience;

    public ConsultantEntity() {}

    public String getConsultantId() { return consultantId; }
    public void setConsultantId(String consultantId) { this.consultantId = consultantId; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public String getEmploymentType() { return employmentType; }
    public void setEmploymentType(String employmentType) { this.employmentType = employmentType; }

    public String getServices() { return services; }
    public void setServices(String services) { this.services = services; }

    public String getRegions() { return regions; }
    public void setRegions(String regions) { this.regions = regions; }

    public String getPools() { return pools; }
    public void setPools(String pools) { this.pools = pools; }

    public String getRestrictions() { return restrictions; }
    public void setRestrictions(String restrictions) { this.restrictions = restrictions; }

    public String getCustomerExperience() { return customerExperience; }
    public void setCustomerExperience(String customerExperience) { this.customerExperience = customerExperience; }
}
