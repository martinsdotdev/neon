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

### Changed

- Migrated from flat layout to default sbt source layout
- Renamed app module to core
- Replaced GroupId with ConsolidationGroupId
- Unabbreviated all identifiers and test descriptions
