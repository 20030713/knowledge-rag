package com.rag.knowledge.rag;

import java.util.Locale;

public enum AnswerStyle {
    STRICT("严谨模式", "strict"),
    BRIEF("简洁模式", "brief"),
    INTERVIEW("面试讲解模式", "interview");

    private final String label;
    private final String cacheKey;

    AnswerStyle(String label, String cacheKey) {
        this.label = label;
        this.cacheKey = cacheKey;
    }

    public String label() {
        return label;
    }

    public String cacheKey() {
        return cacheKey;
    }

    public static AnswerStyle from(String value) {
        if (value == null || value.isBlank()) {
            return STRICT;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        for (AnswerStyle style : values()) {
            if (style.name().equals(normalized) || style.cacheKey.equalsIgnoreCase(value.trim())) {
                return style;
            }
        }
        return STRICT;
    }
}
