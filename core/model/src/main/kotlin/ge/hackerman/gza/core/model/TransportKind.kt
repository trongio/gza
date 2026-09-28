package ge.hackerman.gza.core.model

/**
 * What kind of vehicle serves a stop, route or leg. The gateway says `BUS` for minibuses
 * too; [MINIBUS] can only come from a minibus route id ([RouteId.isMinibus]).
 */
enum class TransportKind { BUS, MINIBUS, METRO, CABLE_CAR, UNKNOWN }
