package quest.gekko.cys.service.connector;

import quest.gekko.cys.domain.Channel;
import quest.gekko.cys.domain.Platform;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface PlatformConnector {
    Platform platform();

    /** Resolve a single channel from a handle or URL (what you already have). */
    Optional<Channel> resolveAndHydrate(String handleOrUrl);

    /** NEW: Search by free text and return a list of channels. */
    default List<Channel> search(String query, int maxResults) { return List.of(); }

    /** Keep your counters method as-is. */
    Map<String, Long> fetchCounters(String platformId);
}