package org.example.mcpserver.service.mapping;

import org.example.mcpserver.repository.domain.*;
import org.mapstruct.Named;
import org.springframework.stereotype.Component;

@Component
public class EntityRefMapper {

    // ---- Entity -> ID ----
    @Named("consultantToId")
    public String consultantToId(ConsultantEntity c) {
        return c == null ? null : c.getConsultantId();
    }

    @Named("customerToId")
    public String customerToId(CustomerEntity c) {
        return c == null ? null : c.getCustomerId();
    }

    @Named("assignmentToId")
    public String assignmentToId(AssignmentEntity a) {
        return a == null ? null : a.getAssignmentId();
    }

    // ---- ID -> Entity reference (stub) ----
    @Named("idToConsultant")
    public ConsultantEntity idToConsultant(String id) {
        if (id == null) return null;
        ConsultantEntity c = new ConsultantEntity();
        c.setConsultantId(id);
        return c;
    }

    @Named("idToCustomer")
    public CustomerEntity idToCustomer(String id) {
        if (id == null) return null;
        CustomerEntity c = new CustomerEntity();
        c.setCustomerId(id);
        return c;
    }

    @Named("idToAssignment")
    public AssignmentEntity idToAssignment(String id) {
        if (id == null) return null;
        AssignmentEntity a = new AssignmentEntity();
        a.setAssignmentId(id);
        return a;
    }
}