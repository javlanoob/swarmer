# Changelog

## [1.0-SNAPSHOT] - 2025-12-16

### Added
- Initial release
- Instant swarm hiding on hitsplat detection
- Wave number overlay system
- High wave filtering options
- XP drop fallback detection
- Configurable font size and color
- ToA region detection

### Features
- Hides scarab swarms immediately when hit
- Shows wave numbers above alive swarms
- Optional high wave number/swarm hiding
- Proper cleanup on region changes
- Lombok integration for cleaner code

### Technical
- Restructured package to `com.javlanoob`
- Added `HitsplatApplied` event subscriber for instant detection
- Implemented proper logging with slf4j
- Added Lombok for getter/setter generation
- Updated to follow RuneLite plugin best practices
