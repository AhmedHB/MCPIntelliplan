package org.example.mcpserver.repository.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "services")
public class ServiceEntity {

    @Id
    @Column(name = "service_code", nullable = false)
    private String serviceCode;

    @Column(name = "description", nullable = false)
    private String description;

    public ServiceEntity() {}

    public String getServiceCode() { return serviceCode; }
    public void setServiceCode(String serviceCode) { this.serviceCode = serviceCode; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}