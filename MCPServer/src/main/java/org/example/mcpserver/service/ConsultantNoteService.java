package org.example.mcpserver.service;

import org.example.mcpserver.dto.ConsultantNoteDTO;
import org.example.mcpserver.repository.AssignmentRepository;
import org.example.mcpserver.repository.ConsultantNoteRepository;
import org.example.mcpserver.repository.ConsultantRepository;
import org.example.mcpserver.repository.CustomerRepository;
import org.example.mcpserver.repository.domain.ConsultantNoteEntity;
import org.example.mcpserver.service.exception.BadRequestException;
import org.example.mcpserver.service.exception.NotFoundException;
import org.example.mcpserver.service.mapping.ConsultantNoteMapper;
import org.example.mcpserver.service.validation.ValidationUtils;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class ConsultantNoteService {

    private final ConsultantNoteRepository noteRepository;
    private final ConsultantRepository consultantRepository;
    private final CustomerRepository customerRepository;
    private final AssignmentRepository assignmentRepository;
    private final ConsultantNoteMapper consultantNoteMapper;

    public ConsultantNoteService(ConsultantNoteRepository noteRepository,
                                 ConsultantRepository consultantRepository,
                                 CustomerRepository customerRepository,
                                 AssignmentRepository assignmentRepository,
                                 ConsultantNoteMapper consultantNoteMapper) {
        this.noteRepository = noteRepository;
        this.consultantRepository = consultantRepository;
        this.customerRepository = customerRepository;
        this.assignmentRepository = assignmentRepository;
        this.consultantNoteMapper = consultantNoteMapper;
    }

    // ================================
    // CREATE
    // ================================
    @Tool(
            name = "consultant_note_create",
            description = "Create a consultant note with validation. assignmentId is optional; if provided it must exist."
    )
    public ConsultantNoteDTO create(ConsultantNoteDTO dto) {

        ValidationUtils.requireNonNull(dto, "note");
        String noteId = ValidationUtils.requireNonBlank(dto.noteId(), "noteId");
        String noteText = ValidationUtils.requireNonBlank(dto.note(), "note");

        if (noteRepository.existsById(noteId)) {
            throw new BadRequestException("Note finns redan: " + noteId);
        }

        // consultantId required
        String consultantId = ValidationUtils.requireNonBlank(dto.consultantId(), "consultantId");
        if (!consultantRepository.existsById(consultantId)) {
            throw new BadRequestException("Ogiltig consultantId: " + consultantId);
        }

        // customerId required
        String customerId = ValidationUtils.requireNonBlank(dto.customerId(), "customerId");
        if (!customerRepository.existsById(customerId)) {
            throw new BadRequestException("Ogiltig customerId: " + customerId);
        }

        // assignmentId optional (if provided must exist)
        String assignmentId = dto.assignmentId();
        if (assignmentId != null) {
            assignmentId = ValidationUtils.requireNonBlank(assignmentId, "assignmentId");
            if (!assignmentRepository.existsById(assignmentId)) {
                throw new BadRequestException("Ogiltig assignmentId: " + assignmentId);
            }
        }

        ConsultantNoteEntity entity = consultantNoteMapper.toEntity(dto);

        // ensure NULL (not empty) if not provided
        if (dto.assignmentId() == null) {
            entity.setAssignment(null);
        }

        // note text (already validated)
        entity.setNote(noteText);

        ConsultantNoteEntity saved = noteRepository.save(entity);
        return consultantNoteMapper.toDto(saved);
    }

    // ================================
    // READ BY ID
    // ================================
    @Tool(
            name = "consultant_note_get_by_id",
            description = "Retrieve a consultant note by its ID."
    )
    @Transactional(readOnly = true)
    public ConsultantNoteDTO getById(String noteId) {

        String id = ValidationUtils.requireNonBlank(noteId, "noteId");

        ConsultantNoteEntity entity = noteRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Note hittades inte: " + id));

        return consultantNoteMapper.toDto(entity);
    }

    // ================================
    // LIST ALL
    // ================================
    @Tool(
            name = "consultant_note_list",
            description = "Return a list of all consultant notes."
    )
    @Transactional(readOnly = true)
    public List<ConsultantNoteDTO> list() {
        return noteRepository.findAll()
                .stream()
                .map(consultantNoteMapper::toDto)
                .toList();
    }

    // ================================
    // UPDATE
    // ================================
    @Tool(
            name = "consultant_note_update",
            description = "Update a consultant note. Supports changing consultant/customer and setting/unsetting assignmentId."
    )
    public ConsultantNoteDTO update(String noteId, ConsultantNoteDTO patch) {

        String id = ValidationUtils.requireNonBlank(noteId, "noteId");
        ValidationUtils.requireNonNull(patch, "note");

        ConsultantNoteEntity existing = noteRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Note hittades inte: " + id));

        // patch note
        if (patch.note() != null) {
            existing.setNote(ValidationUtils.requireNonBlank(patch.note(), "note"));
        }

        // patch consultantId
        if (patch.consultantId() != null) {
            String consultantId = ValidationUtils.requireNonBlank(patch.consultantId(), "consultantId");
            if (!consultantRepository.existsById(consultantId)) {
                throw new BadRequestException("Ogiltig consultantId: " + consultantId);
            }
            // mapstruct stub entity from id
            existing.setConsultant(consultantNoteMapper.toEntity(
                    new ConsultantNoteDTO(null, consultantId, null, null, null)
            ).getConsultant());
        }

        // patch customerId
        if (patch.customerId() != null) {
            String customerId = ValidationUtils.requireNonBlank(patch.customerId(), "customerId");
            if (!customerRepository.existsById(customerId)) {
                throw new BadRequestException("Ogiltig customerId: " + customerId);
            }
            existing.setCustomer(consultantNoteMapper.toEntity(
                    new ConsultantNoteDTO(null, null, customerId, null, null)
            ).getCustomer());
        }

        // assignment: allow set/unset
        // - patch.assignmentId() == null  => UNSET
        // - patch.assignmentId() != null  => SET (must exist)
        if (patch.assignmentId() == null) {
            existing.setAssignment(null);
        } else {
            String assignmentId = ValidationUtils.requireNonBlank(patch.assignmentId(), "assignmentId");
            if (!assignmentRepository.existsById(assignmentId)) {
                throw new BadRequestException("Ogiltig assignmentId: " + assignmentId);
            }
            existing.setAssignment(consultantNoteMapper.toEntity(
                    new ConsultantNoteDTO(null, null, null, assignmentId, null)
            ).getAssignment());
        }

        ConsultantNoteEntity saved = noteRepository.save(existing);
        return consultantNoteMapper.toDto(saved);
    }

    // ================================
    // DELETE
    // ================================
    @Tool(
            name = "consultant_note_delete",
            description = "Delete a consultant note by its ID."
    )
    public void delete(String noteId) {

        String id = ValidationUtils.requireNonBlank(noteId, "noteId");

        if (!noteRepository.existsById(id)) {
            throw new NotFoundException("Note hittades inte: " + id);
        }

        noteRepository.deleteById(id);
    }
}