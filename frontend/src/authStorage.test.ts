import { beforeEach, describe, expect, it } from "vitest";
import { clearLegacyCredentials, loadRememberedUsername, persistRememberedUsername } from "./authStorage";

describe("auth storage", () => {
  beforeEach(() => localStorage.clear());

  it("removes credentials stored by older versions", () => {
    localStorage.setItem("knowledge-rag-last-password", "plain-text-password");
    localStorage.setItem("knowledge-rag-remember-password", "true");

    clearLegacyCredentials();

    expect(localStorage.getItem("knowledge-rag-last-password")).toBeNull();
    expect(localStorage.getItem("knowledge-rag-remember-password")).toBeNull();
  });

  it("persists only the username when requested", () => {
    persistRememberedUsername("alice", true);

    expect(loadRememberedUsername()).toBe("alice");
    expect([...Array(localStorage.length)].map((_, index) => localStorage.key(index))).toEqual([
      "knowledge-rag-last-username"
    ]);
  });

  it("forgets the username without retaining credentials", () => {
    persistRememberedUsername("alice", true);
    persistRememberedUsername("alice", false);

    expect(loadRememberedUsername()).toBe("");
    expect(localStorage.length).toBe(0);
  });
});
