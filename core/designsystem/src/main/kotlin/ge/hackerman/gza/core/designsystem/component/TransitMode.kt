package ge.hackerman.gza.core.designsystem.component

/**
 * The kind of vehicle as the UI shows it. Minibus is its own kind even though the gateway
 * reports minibuses as `BUS` (docs/TTC_API.md); feature code maps domain types onto this.
 */
enum class TransitMode { Bus, Minibus, Metro, CableCar }
