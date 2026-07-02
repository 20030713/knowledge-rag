package com.rag.knowledge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.rate-limit")
public class RateLimitProperties {

    private boolean enabled = true;

    private Rule login = new Rule(10, 60);

    private Rule ragAsk = new Rule(20, 60);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Rule getLogin() {
        return login;
    }

    public void setLogin(Rule login) {
        this.login = login;
    }

    public Rule getRagAsk() {
        return ragAsk;
    }

    public void setRagAsk(Rule ragAsk) {
        this.ragAsk = ragAsk;
    }

    public record Rule(int limit, int windowSeconds) {
    }
}
