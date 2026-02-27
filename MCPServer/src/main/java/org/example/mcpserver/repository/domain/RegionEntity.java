package org.example.mcpserver.repository.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "regions")
public class RegionEntity {

    @Id
    @Column(name = "region_code", nullable = false)
    private String regionCode;

    @Column(name = "name", nullable = false)
    private String name;

    public RegionEntity() {}

    public String getRegionCode() { return regionCode; }
    public void setRegionCode(String regionCode) { this.regionCode = regionCode; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
