package org.example.mcpserver.repository.domain;

import jakarta.persistence.*;

import jakarta.persistence.*;

@Entity
@Table(name = "pools")
public class PoolEntity {

    @Id
    @Column(name = "pool_id", nullable = false)
    private String poolId;

    @Column(name = "description", nullable = false)
    private String description;

    @Column(name = "regions")
    private String regions;

    public PoolEntity() {}

    public String getPoolId() { return poolId; }
    public void setPoolId(String poolId) { this.poolId = poolId; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getRegions() { return regions; }
    public void setRegions(String regions) { this.regions = regions; }
}
