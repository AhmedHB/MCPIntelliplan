package org.example.mcpserver.repository.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "consultant_notes")
public class ConsultantNoteEntity {

    @Id
    @Column(name = "note_id", nullable = false)
    private String noteId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "consultant_id", nullable = false)
    private ConsultantEntity consultant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private CustomerEntity customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignment_id")
    private AssignmentEntity assignment;

    @Column(name = "note", nullable = false)
    private String note;

    public ConsultantNoteEntity() {}

    public String getNoteId() { return noteId; }
    public void setNoteId(String noteId) { this.noteId = noteId; }

    public ConsultantEntity getConsultant() { return consultant; }
    public void setConsultant(ConsultantEntity consultant) { this.consultant = consultant; }

    public CustomerEntity getCustomer() { return customer; }
    public void setCustomer(CustomerEntity customer) { this.customer = customer; }

    public AssignmentEntity getAssignment() { return assignment; }
    public void setAssignment(AssignmentEntity assignment) { this.assignment = assignment; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
