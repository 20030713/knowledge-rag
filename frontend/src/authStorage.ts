const LAST_USERNAME_KEY = "knowledge-rag-last-username";
const LEGACY_PASSWORD_KEY = "knowledge-rag-last-password";
const LEGACY_REMEMBER_PASSWORD_KEY = "knowledge-rag-remember-password";

export function clearLegacyCredentials(storage: Storage = window.localStorage) {
  storage.removeItem(LEGACY_PASSWORD_KEY);
  storage.removeItem(LEGACY_REMEMBER_PASSWORD_KEY);
}

export function loadRememberedUsername(storage: Storage = window.localStorage) {
  clearLegacyCredentials(storage);
  return storage.getItem(LAST_USERNAME_KEY) ?? "";
}

export function persistRememberedUsername(username: string, remember: boolean, storage: Storage = window.localStorage) {
  clearLegacyCredentials(storage);
  if (remember) {
    storage.setItem(LAST_USERNAME_KEY, username);
  } else {
    storage.removeItem(LAST_USERNAME_KEY);
  }
}
