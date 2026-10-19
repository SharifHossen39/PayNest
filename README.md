# PayNest

A learning project for a secure USD wallet API: register, sign in, inspect your
profile and wallet, transfer money, and read participant-only transaction history.
It is one Spring Boot application backed by developer-managed PostgreSQL.

Prerequisites: JDK **25**, PostgreSQL **18.6** with `psql`, and PowerShell or a Unix
shell. The committed Maven Wrapper downloads Maven **3.10.0**; a separate Maven
installation is unnecessary. Dependencies require network access on the first build.

Follow the [local development runbook](docs/local-development.md) for separate
development/test database setup, all environment variables, direct startup,
manual wallet funding, the complete seven-operation HTTP journey, tests, and
shutdown. Configuration examples contain placeholders; `.env` is not loaded
automatically.
