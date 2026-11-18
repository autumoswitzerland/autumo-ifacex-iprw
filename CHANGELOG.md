# Changelog

All notable changes to this project will be documented in this file.

## [3.0.0] - 2026-11-18

### Changed
- **Version**: Updated from 2.1.0 to 3.0.0.
- **Interface Format**: Converted all interface examples to the `.ifcx` YAML format (new interface system), replacing the previous template-based format.
- **New Module**: Added new module `mod-nifi` with NiFi Site-to-Site (S2S) reader/writer support and full integration test setup.
- **Dependency Tests**: Added `DependencyCompatibilityTest` for all modules using Mockito mocks.
- **Dependency Updates**: Updated dependencies, including `autumo-beetroot` to 3.1.3, Microsoft Graph to version 6, and others.
- **Documentation**: Updated documentation and README for the new version and features.

## [2.1.0] - 2024-07-26

### Changed
- **Version**: Updated from 2.0.0 to 2.1.0.
- **Minor Enhancements**: Minor improvements to documentation and interface definitions.

## [2.0.0] - 2024-07-26

### Added
- Initial release of autumo-ifaceX.
- Core functionality for reader/writer modules across multiple protocols.
- Basic interface definitions and configuration mechanisms.

### Changed
- Established core architecture and build process with Maven.

### Fixed
- Minor bug fixes in interface definitions and configuration handling.

