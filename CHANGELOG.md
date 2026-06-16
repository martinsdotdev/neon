# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### Added

- Web frontend with TanStack Start, React 19, shadcn/ui, and Tailwind CSS v4
- Ultracite (oxlint + oxfmt) and lefthook for frontend code quality
- CLAUDE.md with project guidance for Claude Code
- VerificationProfile gate to TaskCompletionService
- 5 application services covering full domain flow (TaskCompletion, WaveRelease, WavePlanning, WaveCancellation, TransportOrderConfirmation)
- Slot module with workstation slot management
- TaskCompletionService with 5-step completion cascade
- Scaladoc to every public method and type across all 14 modules
- `TaskCompletionCascade` pure decision module shared by the sync and async completion services
- Shared event-sourced entity scaffolding (`EventSourcedEntity`, `PekkoEntityRepository`) in `common`, adopted by all 14 slices
- `ProblemMapper` mapping every domain error ADT to an RFC 9457 Problem Details response
- Per-module `<X>ProjectionSchema` owners for read-side table, column, and SQL definitions
- `unwrapForQuery` query-consumption helper in `packages/client` (throws `ApiRequestError`, optional dev-mock fallback)
- Shared `Location` and `Sku` entity types in `packages/domain` and a Scala `PermissionContractSuite` guarding the TS↔Scala permission mirror
- Vitest coverage for the API client, query helper, and domain task transitions
- Shared core test fixtures (`DomainFactories`, in-memory repositories) and `RouteSuiteBase` auth scaffolding
- `CONTEXT.md` ubiquitous-language glossary

### Changed

- Migrated from flat layout to default sbt source layout
- Renamed app module to core
- Replaced GroupId with ConsolidationGroupId
- Unabbreviated all identifiers and test descriptions
- `TaskCompletionService` and `AsyncTaskCompletionService` are now thin load/decide/persist shells over `TaskCompletionCascade`
- HTTP routes return `application/problem+json` bodies for domain errors (status codes unchanged)
- Web queries surface API failures to the user in production instead of rendering empty lists silently

### Fixed

- Async (production) task completion now consumes and deallocates stock — the async path previously skipped stock handling entirely
- A shortpicked task completion no longer completes its wave or consolidation group while the open replacement is still in flight (projection-lag race in the async path)
- Removed the dead `inbound:receive` permission key from the TypeScript domain mirror
