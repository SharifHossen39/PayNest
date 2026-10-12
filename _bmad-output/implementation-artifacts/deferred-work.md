- source_spec: `C:\Users\BS01039\Desktop\Java-PayNest\_bmad-output\implementation-artifacts\spec-1-1-establish-runnable-paynest-foundation.md`
  summary: Verify and record the Unix executable bit for `mvnw` when the PayNest Git repository is initialized.
  evidence: The workspace has no Git metadata, so the future clean-checkout file mode cannot be proven; `./mvnw` does execute successfully in the current Linux container-mounted workspace.
