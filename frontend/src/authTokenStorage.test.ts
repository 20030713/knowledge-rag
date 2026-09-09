import { beforeEach, describe, expect, it } from "vitest";
import { clearToken, getToken, setToken } from "./api";

const TOKEN_KEY = "knowledge-rag-token";

describe("authentication token storage", () => {
  beforeEach(() => {
    localStorage.clear();
    sessionStorage.clear();
  });

  it("stores new tokens only for the current browser session", () => {
    setToken("session-token");

    expect(sessionStorage.getItem(TOKEN_KEY)).toBe("session-token");
    expect(localStorage.getItem(TOKEN_KEY)).toBeNull();
  });

  it("migrates persistent tokens created by older versions", () => {
    localStorage.setItem(TOKEN_KEY, "legacy-token");

    expect(getToken()).toBe("legacy-token");
    expect(sessionStorage.getItem(TOKEN_KEY)).toBe("legacy-token");
    expect(localStorage.getItem(TOKEN_KEY)).toBeNull();
  });

  it("clears both current and legacy token locations", () => {
    sessionStorage.setItem(TOKEN_KEY, "session-token");
    localStorage.setItem(TOKEN_KEY, "legacy-token");

    clearToken();

    expect(sessionStorage.getItem(TOKEN_KEY)).toBeNull();
    expect(localStorage.getItem(TOKEN_KEY)).toBeNull();
  });
});
