# ADR 0003: API key authentication with encrypted storage

Status: Accepted

Date: 2026-09-26

## Context

ZCode desktop supports Z.ai and BigModel OAuth login plus plain API keys, and stores API keys in plain text inside its configuration files (OAuth tokens go into an encrypted credentials file). The product requirements for this app explicitly exclude OAuth: there is no account login, no Coding Plan account integration, and no idle-time tasks. Users supply their own API keys for the Z.ai Open Platform, the Z.ai Coding Plan, or BigModel endpoints.

A mobile device is a different threat environment than a desktop: app-private storage can be exposed by backups, and configuration files can be synced or exported by accident.

## Decision

1. Authentication is API key only. No OAuth, no login screens, no account features.
2. The API key is stored in Android Keystore backed encrypted storage (EncryptedSharedPreferences or DataStore plus Tink; the concrete mechanism is chosen in milestone M2). The key is never written to plain text configuration files, logs, exports, or crash reports.
3. `provider_config.json` stays compatible with the ZCode structure (provider list, base URLs, enabled flags, per-model settings) but holds a reference id to the encrypted credential instead of the key itself. This is the single deliberate deviation from the ZCode config format and it is documented here and in `docs/architecture.md`.
4. Backups are disabled (`allowBackup=false`) so encrypted credentials and configuration are not copied off the device by the system backup service.

## Consequences

- Configuration files can be shared or inspected without leaking credentials, at the cost of a small format deviation from desktop ZCode.
- Switching providers or models reuses the same encrypted storage and does not interrupt running sessions.
- Losing the app data (uninstall) loses the key reference, so the user re-enters the API key after a reinstall. That is acceptable because the user holds the key.

## References

- ZCode repository and configuration documentation: https://github.com/zai-org/ZCode and https://zcode.z.ai/en/docs
- Android Keystore overview: https://developer.android.com/privacy-and-security/keystore
