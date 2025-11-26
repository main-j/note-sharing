package com.project.login.model.response.admin;

import com.project.login.model.entity.NoteEntity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NoteAdminSummary {
    private Long id;
    private String author;
    private String space;
    private String notebook;
    private String tag;
    private String preview;

    public static NoteAdminSummary from(NoteEntity n) {
        String author = n.getAuthorName();
        String space = null;
        String notebook = null;
        String tag = (n.getTags() != null && !n.getTags().isEmpty()) ? n.getTags().get(0) : null;
        String preview = n.getTitle();
        return new NoteAdminSummary(n.getId(), author, space, notebook, tag, preview);
    }
}
