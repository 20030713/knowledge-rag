package com.rag.knowledge.domain.enums;

import java.util.Locale;

public enum KnowledgeBaseMemberRole {
    VIEWER(1),
    EDITOR(2),
    ADMIN(3),
    OWNER(4);

    private final int level;

    KnowledgeBaseMemberRole(int level) {
        this.level = level;
    }

    public boolean atLeast(KnowledgeBaseMemberRole required) {
        return level >= required.level;
    }

    public static KnowledgeBaseMemberRole from(String value) {
        if (value == null || value.isBlank()) {
            return VIEWER;
        }
        return KnowledgeBaseMemberRole.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
