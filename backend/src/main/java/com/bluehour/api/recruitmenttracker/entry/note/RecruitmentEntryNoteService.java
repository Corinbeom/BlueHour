package com.bluehour.api.recruitmenttracker.entry.note;

import com.bluehour.api.recruitmenttracker.entry.RecruitmentEntryService;
import com.bluehour.common.ResourceNotFoundException;
import com.bluehour.domain.recruitmenttracker.entry.model.RecruitmentEntry;
import com.bluehour.domain.recruitmenttracker.note.model.RecruitmentEntryNote;
import com.bluehour.domain.recruitmenttracker.note.port.RecruitmentEntryNoteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class RecruitmentEntryNoteService {

    private final RecruitmentEntryService entryService;
    private final RecruitmentEntryNoteRepository noteRepository;

    public RecruitmentEntryNoteService(
            RecruitmentEntryService entryService,
            RecruitmentEntryNoteRepository noteRepository
    ) {
        this.entryService = entryService;
        this.noteRepository = noteRepository;
    }

    @Transactional(readOnly = true)
    public List<RecruitmentEntryNote> list(Long entryId) {
        // entry 존재 검증(없으면 404)
        entryService.get(entryId);
        return noteRepository.findAllByEntryId(entryId);
    }

    @Transactional(readOnly = true)
    public List<RecruitmentEntryNote> list(Long entryId, Long memberId) {
        entryService.get(entryId, memberId);
        return noteRepository.findAllByEntryId(entryId);
    }

    public RecruitmentEntryNote create(Long entryId, String content) {
        RecruitmentEntry entry = entryService.get(entryId);
        RecruitmentEntryNote note = new RecruitmentEntryNote(entry, content);
        return noteRepository.save(note);
    }

    public RecruitmentEntryNote create(Long entryId, Long memberId, String content) {
        RecruitmentEntry entry = entryService.get(entryId, memberId);
        RecruitmentEntryNote note = new RecruitmentEntryNote(entry, content);
        return noteRepository.save(note);
    }

    public RecruitmentEntryNote update(Long entryId, Long noteId, String content) {
        RecruitmentEntryNote note = get(noteId);
        ensureBelongsToEntry(entryId, note);
        note.updateContent(content);
        return note;
    }

    public RecruitmentEntryNote update(Long entryId, Long memberId, Long noteId, String content) {
        entryService.get(entryId, memberId);
        RecruitmentEntryNote note = get(noteId);
        ensureBelongsToEntry(entryId, note);
        note.updateContent(content);
        return note;
    }

    public void delete(Long entryId, Long noteId) {
        RecruitmentEntryNote note = get(noteId);
        ensureBelongsToEntry(entryId, note);
        noteRepository.delete(note);
    }

    public void delete(Long entryId, Long memberId, Long noteId) {
        entryService.get(entryId, memberId);
        RecruitmentEntryNote note = get(noteId);
        ensureBelongsToEntry(entryId, note);
        noteRepository.delete(note);
    }

    @Transactional(readOnly = true)
    public RecruitmentEntryNote get(Long noteId) {
        return noteRepository.findById(noteId)
                .orElseThrow(() -> new ResourceNotFoundException("RecruitmentEntryNote를 찾을 수 없습니다. id=" + noteId));
    }

    private void ensureBelongsToEntry(Long entryId, RecruitmentEntryNote note) {
        Long actualEntryId = note.getEntry().getId();
        if (actualEntryId == null || !actualEntryId.equals(entryId)) {
            throw new ResourceNotFoundException("RecruitmentEntryNote를 찾을 수 없습니다. entryId=" + entryId + ", noteId=" + note.getId());
        }
    }
}

