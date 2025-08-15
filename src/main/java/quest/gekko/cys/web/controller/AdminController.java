package quest.gekko.cys.web.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import quest.gekko.cys.domain.Channel;
import quest.gekko.cys.domain.Platform;
import quest.gekko.cys.repository.ChannelRepository;
import quest.gekko.cys.repository.DailyStatRepository;
import quest.gekko.cys.service.core.RankingService;
import quest.gekko.cys.service.core.StatsService;
import quest.gekko.cys.service.discovery.SmartDiscoveryService;
import quest.gekko.cys.service.integration.connector.PlatformConnector;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final List<PlatformConnector> connectors;
    private final ChannelRepository channelRepo;
    private final StatsService statsService;
    private final RankingService rankingService;
    private final DailyStatRepository statRepo;
    private final SmartDiscoveryService smartDiscoveryService;

    // Original ingest endpoint
    @PostMapping("/ingest/{platform}")
    @ResponseBody
    public String ingest(@PathVariable Platform platform, @RequestParam String handleOrUrl) {
        var connector = connectors.stream().filter(c -> c.platform() == platform).findFirst().orElseThrow();
        var chOpt = connector.resolveAndHydrate(handleOrUrl);
        if (chOpt.isEmpty()) return "Not found";

        // Save the channel
        Channel saved = channelRepo.findByPlatformAndPlatformId(platform, chOpt.get().getPlatformId())
                .map(existing -> {
                    existing.setTitle(chOpt.get().getTitle());
                    existing.setHandle(chOpt.get().getHandle());
                    existing.setAvatarUrl(chOpt.get().getAvatarUrl());
                    existing.setCountry(chOpt.get().getCountry());
                    return channelRepo.save(existing);
                })
                .orElseGet(() -> channelRepo.save(chOpt.get()));

        return "OK: " + saved.getHandle() + " (ID: " + saved.getId() + ")";
    }

    // Manual snapshot trigger for a specific channel
    @PostMapping("/snapshot/{channelId}")
    @ResponseBody
    public String snapshot(@PathVariable Long channelId) {
        Channel channel = channelRepo.findById(channelId).orElse(null);
        if (channel == null) return "Channel not found";

        var connector = connectors.stream()
                .filter(c -> c.platform() == channel.getPlatform())
                .findFirst()
                .orElse(null);

        if (connector == null) return "No connector for platform: " + channel.getPlatform();

        try {
            var counters = connector.fetchCounters(channel.getPlatformId());
            LocalDate today = LocalDate.now(ZoneOffset.UTC);
            statsService.snapshot(channel, counters, today);
            return "OK: Snapshot created for " + channel.getHandle() + " - " + counters;
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    // Manual snapshot for ALL channels (synchronous for backwards compatibility)
    @PostMapping("/snapshot-all")
    @ResponseBody
    public String snapshotAll() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        int processed = 0;

        for (PlatformConnector connector : connectors) {
            var channels = channelRepo.findAll().stream()
                    .filter(c -> c.getPlatform() == connector.platform())
                    .toList();

            for (Channel channel : channels) {
                try {
                    var counters = connector.fetchCounters(channel.getPlatformId());
                    statsService.snapshot(channel, counters, today);
                    processed++;

                    // Small delay to be nice to APIs
                    Thread.sleep(500);
                } catch (Exception e) {
                    // Continue with next channel on error
                    System.err.println("Error processing " + channel.getHandle() + ": " + e.getMessage());
                }
            }
        }

        return "OK: Processed " + processed + " channels";
    }

    // List all channels for debugging
    @GetMapping("/channels")
    @ResponseBody
    public String listChannels() {
        var channels = channelRepo.findAll();
        if (channels.isEmpty()) return "No channels found";

        StringBuilder sb = new StringBuilder("Channels:\n");
        for (Channel c : channels) {
            sb.append("- ID: ").append(c.getId())
                    .append(", Platform: ").append(c.getPlatform())
                    .append(", PlatformID: ").append(c.getPlatformId())
                    .append(", Handle: ").append(c.getHandle())
                    .append(", Title: ").append(c.getTitle())
                    .append("\n");
        }
        return sb.toString();
    }

    // DEBUG YOUTUBE API
    @PostMapping("/debug-youtube")
    @ResponseBody
    public String debugYouTube(@RequestParam String handle) {
        try {
            var youtubeConnector = connectors.stream()
                    .filter(c -> c.platform() == Platform.YOUTUBE)
                    .findFirst()
                    .orElse(null);

            if (youtubeConnector == null) {
                return "❌ YouTube connector not found";
            }

            StringBuilder result = new StringBuilder();
            result.append("🔍 Testing YouTube API for: ").append(handle).append("\n\n");

            // Test API key
            result.append("API Key Status: ");
            try {
                java.lang.reflect.Field field = youtubeConnector.getClass().getDeclaredField("apiKey");
                field.setAccessible(true);
                String apiKey = (String) field.get(youtubeConnector);
                result.append(apiKey != null && !apiKey.isBlank() ? "✅ Present" : "❌ Missing/Empty").append("\n");
                result.append("API Key length: ").append(apiKey != null ? apiKey.length() : 0).append("\n\n");
            } catch (Exception e) {
                result.append("❌ Error checking API key: ").append(e.getMessage()).append("\n\n");
            }

            // Test resolveAndHydrate
            result.append("Testing resolveAndHydrate:\n");
            try {
                var channel = youtubeConnector.resolveAndHydrate(handle);
                if (channel.isPresent()) {
                    var c = channel.get();
                    result.append("✅ Found channel!\n");
                    result.append("- ID: ").append(c.getPlatformId()).append("\n");
                    result.append("- Title: ").append(c.getTitle()).append("\n");
                    result.append("- Handle: ").append(c.getHandle()).append("\n");
                    result.append("- Avatar: ").append(c.getAvatarUrl() != null ? "✅" : "❌").append("\n");
                } else {
                    result.append("❌ Channel not found via resolveAndHydrate\n");
                }
            } catch (Exception e) {
                result.append("❌ Error in resolveAndHydrate: ").append(e.getMessage()).append("\n");
                result.append("Stack trace: ").append(java.util.Arrays.toString(e.getStackTrace())).append("\n");
            }

            // Test search
            result.append("\nTesting search API:\n");
            try {
                var channels = youtubeConnector.search(handle, 5);
                result.append("Found ").append(channels.size()).append(" channels via search\n");
                for (var c : channels) {
                    result.append("- ").append(c.getTitle()).append(" (").append(c.getHandle()).append(")\n");
                }
            } catch (Exception e) {
                result.append("❌ Error in search: ").append(e.getMessage()).append("\n");
            }

            return result.toString();

        } catch (Exception e) {
            return "❌ Debug failed: " + e.getMessage() + "\n" + java.util.Arrays.toString(e.getStackTrace());
        }
    }

    // ENHANCED ASYNC DISCOVERY METHODS

    // Seed popular channels (async)
    @PostMapping("/seed-popular")
    @ResponseBody
    public String seedPopularChannels() {
        try {
            // Trigger async seeding
            smartDiscoveryService.seedPopular();
            return "✅ Popular channel seeding started! This will run in the background.";
        } catch (Exception e) {
            return "❌ Error starting popular channel seeding: " + e.getMessage();
        }
    }

    // Manual discovery trigger (fixed)
    @PostMapping("/discover-now")
    @ResponseBody
    public String discoverNow() {
        try {
            String result = smartDiscoveryService.manualDiscovery();
            return result;
        } catch (Exception e) {
            return "❌ Error: " + e.getMessage();
        }
    }

    // Mass discovery - NEW powerful discovery method (async)
    @PostMapping("/mass-discovery")
    @ResponseBody
    public String massDiscovery() {
        try {
            return smartDiscoveryService.triggerMassDiscovery();
        } catch (Exception e) {
            return "❌ Error: " + e.getMessage();
        }
    }

    // Trending discovery - NEW (async)
    @PostMapping("/discover-trending")
    @ResponseBody
    public String discoverTrending() {
        try {
            smartDiscoveryService.discoverTrending();
            return "✅ Trending discovery started! Running in background with parallel processing.";
        } catch (Exception e) {
            return "❌ Error: " + e.getMessage();
        }
    }

    // Related channels discovery - NEW (async)
    @PostMapping("/discover-related")
    @ResponseBody
    public String discoverRelated() {
        try {
            smartDiscoveryService.discoverRelated();
            return "✅ Related channels discovery started! Running in background with parallel processing.";
        } catch (Exception e) {
            return "❌ Error: " + e.getMessage();
        }
    }

    // Batch snapshot for channels without recent data - NEW (async)
    @PostMapping("/batch-snapshot")
    @ResponseBody
    public String batchSnapshot(@RequestParam(defaultValue = "100") int limit) {
        try {
            // Start async batch snapshot
            CompletableFuture<String> future = smartDiscoveryService.batchSnapshotAsync(limit);
            return "✅ Batch snapshot started! Processing up to " + limit + " channels in the background.";
        } catch (Exception e) {
            return "❌ Error: " + e.getMessage();
        }
    }

    // Get discovery statistics - ENHANCED
    @GetMapping("/discovery-stats")
    @ResponseBody
    public String getDiscoveryStats() {
        try {
            return smartDiscoveryService.getEnhancedStats();
        } catch (Exception e) {
            return "❌ Error getting stats: " + e.getMessage();
        }
    }

    // Database health check - NEW
    @GetMapping("/health-check")
    @ResponseBody
    public String healthCheck() {
        try {
            long totalChannels = channelRepo.count();
            long staleChannels = channelRepo.findAll().stream()
                    .filter(c -> {
                        var latestStat = statRepo.findTopByChannelIdOrderBySnapshotDateDesc(c.getId());
                        return latestStat.isEmpty() ||
                                latestStat.get().getSnapshotDate().isBefore(LocalDate.now().minusDays(7));
                    })
                    .count();

            long activeChannels = totalChannels - staleChannels;
            long youtubeChannels = channelRepo.findAll().stream()
                    .filter(c -> c.getPlatform() == Platform.YOUTUBE)
                    .count();

            long twitchChannels = channelRepo.findAll().stream()
                    .filter(c -> c.getPlatform() == Platform.TWITCH)
                    .count();

            return String.format(
                    """
                    🏥 Database Health Check:
                    
                    📊 Total Channels: %d
                       • YouTube: %d
                       • Twitch: %d
                    ✅ Active (recent data): %d
                    ⚠️  Stale (>7 days old): %d
                    
                    📈 Health Score: %.1f%%
                    
                    🚀 Performance Mode: Multi-threaded discovery active
                    
                    %s
                    """,
                    totalChannels, youtubeChannels, twitchChannels, activeChannels, staleChannels,
                    totalChannels > 0 ? (activeChannels * 100.0 / totalChannels) : 0,
                    staleChannels > 0 ? "💡 Consider running batch snapshot to refresh stale channels" : "✅ All channels are up to date!"
            );
        } catch (Exception e) {
            return "❌ Error: " + e.getMessage();
        }
    }

    // Discover channels by search term (fixed)
    @PostMapping("/discover")
    @ResponseBody
    public String discoverChannels(@RequestParam String searchTerm,
                                   @RequestParam(defaultValue = "10") int maxResults) {
        try {
            smartDiscoveryService.opportunisticDiscovery(searchTerm);
            return "✅ Discovery started for '" + searchTerm + "' - results will appear soon!";
        } catch (Exception e) {
            return "❌ Error: " + e.getMessage();
        }
    }

    // HANDLE FIXING METHODS
    @PostMapping("/fix-unknown-handles")
    @ResponseBody
    public String fixUnknownHandles(@RequestParam(defaultValue = "100") int batchSize) {
        try {
            var youtubeConnector = connectors.stream()
                    .filter(c -> c.platform() == Platform.YOUTUBE)
                    .findFirst()
                    .orElse(null);

            if (youtubeConnector == null) {
                return "❌ YouTube connector not available";
            }

            // Find ALL channels with unknown handles (not just first 100)
            var allChannels = channelRepo.findAll();
            var channelsWithUnknownHandles = allChannels.stream()
                    .filter(c -> c.getPlatform() == Platform.YOUTUBE)
                    .filter(c -> c.getHandle() == null ||
                            c.getHandle().equals("@unknown") ||
                            c.getHandle().startsWith("@unknown") ||
                            c.getHandle().equals("@Channel") ||
                            c.getHandle().matches("@[a-zA-Z0-9]{8}") || // Likely auto-generated from channel ID
                            c.getHandle().matches("@UC[a-zA-Z0-9]{6,}")) // Channel ID based handles
                    .toList();

            System.out.println("Found " + channelsWithUnknownHandles.size() + " channels with unknown handles");

            if (channelsWithUnknownHandles.isEmpty()) {
                return "✅ No channels with unknown handles found!";
            }

            // Process in batches to avoid overwhelming the API
            int totalFixed = 0;
            int batchCount = 0;
            int maxBatches = (int) Math.ceil((double) channelsWithUnknownHandles.size() / batchSize);

            for (int i = 0; i < channelsWithUnknownHandles.size(); i += batchSize) {
                batchCount++;
                int endIndex = Math.min(i + batchSize, channelsWithUnknownHandles.size());
                var batch = channelsWithUnknownHandles.subList(i, endIndex);

                System.out.println("Processing batch " + batchCount + "/" + maxBatches + " (" + batch.size() + " channels)");

                int batchFixed = 0;
                for (Channel channel : batch) {
                    try {
                        // Try to get better handle by re-resolving the channel
                        var updatedChannel = youtubeConnector.resolveAndHydrate(channel.getPlatformId());
                        if (updatedChannel.isPresent()) {
                            var updated = updatedChannel.get();
                            boolean wasUpdated = false;

                            // Update handle if we got a better one
                            if (updated.getHandle() != null &&
                                    !updated.getHandle().equals("@unknown") &&
                                    !updated.getHandle().startsWith("@unknown") &&
                                    !updated.getHandle().equals(channel.getHandle())) {

                                channel.setHandle(updated.getHandle());
                                wasUpdated = true;
                            }

                            // Update other missing fields while we're at it
                            if (channel.getTitle() == null || channel.getTitle().equals("Unknown")) {
                                channel.setTitle(updated.getTitle());
                                wasUpdated = true;
                            }
                            if (channel.getAvatarUrl() == null || channel.getAvatarUrl().isBlank()) {
                                channel.setAvatarUrl(updated.getAvatarUrl());
                                wasUpdated = true;
                            }

                            if (wasUpdated) {
                                channelRepo.save(channel);
                                batchFixed++;
                                totalFixed++;
                            }
                        } else {
                            // If we couldn't resolve, generate a better handle from the title
                            String betterHandle = generateHandleFromTitle(channel.getTitle(), channel.getPlatformId());
                            if (!betterHandle.equals(channel.getHandle())) {
                                channel.setHandle(betterHandle);
                                channelRepo.save(channel);
                                batchFixed++;
                                totalFixed++;
                            }
                        }

                        // Small delay to be nice to API
                        Thread.sleep(100);

                    } catch (Exception e) {
                        System.err.println("Error fixing handle for " + channel.getPlatformId() + ": " + e.getMessage());
                    }
                }

                System.out.println("Batch " + batchCount + " completed: fixed " + batchFixed + " channels");

                // Longer pause between batches
                if (i + batchSize < channelsWithUnknownHandles.size()) {
                    Thread.sleep(1000);
                }
            }

            return String.format("✅ Fixed handles for %d/%d channels across %d batches",
                    totalFixed, channelsWithUnknownHandles.size(), batchCount);

        } catch (Exception e) {
            return "❌ Error during handle fix: " + e.getMessage();
        }
    }

    // Process channels by page range
    @PostMapping("/fix-handles-range")
    @ResponseBody
    public String fixHandlesInRange(@RequestParam int startPage, @RequestParam int endPage, @RequestParam(defaultValue = "20") int pageSize) {
        try {
            var youtubeConnector = connectors.stream()
                    .filter(c -> c.platform() == Platform.YOUTUBE)
                    .findFirst()
                    .orElse(null);

            if (youtubeConnector == null) {
                return "❌ YouTube connector not available";
            }

            // Get channels in the specified page range
            int startIndex = startPage * pageSize;
            int totalChannels = (int) channelRepo.count();

            if (startIndex >= totalChannels) {
                return "❌ Start page exceeds total channels";
            }

            var channelsInRange = channelRepo.findAll().stream()
                    .filter(c -> c.getPlatform() == Platform.YOUTUBE)
                    .skip(startIndex)
                    .limit((endPage - startPage + 1) * pageSize)
                    .toList();

            int fixed = 0;
            for (Channel channel : channelsInRange) {
                try {
                    // Check if this channel needs fixing
                    if (channel.getHandle() == null ||
                            channel.getHandle().equals("@unknown") ||
                            channel.getHandle().startsWith("@unknown") ||
                            channel.getHandle().matches("@[a-zA-Z0-9]{8}")) {

                        var updated = youtubeConnector.resolveAndHydrate(channel.getPlatformId());
                        if (updated.isPresent()) {
                            var u = updated.get();
                            if (u.getHandle() != null && !u.getHandle().startsWith("@unknown")) {
                                channel.setHandle(u.getHandle());
                                channel.setTitle(u.getTitle());
                                channel.setAvatarUrl(u.getAvatarUrl());
                                channelRepo.save(channel);
                                fixed++;
                            }
                        }
                    }

                    Thread.sleep(150);
                } catch (Exception e) {
                    System.err.println("Error processing channel " + channel.getId() + ": " + e.getMessage());
                }
            }

            return String.format("✅ Fixed %d channels in pages %d-%d", fixed, startPage, endPage);

        } catch (Exception e) {
            return "❌ Error: " + e.getMessage();
        }
    }

    /**
     * Generate a reasonable handle from channel title
     */
    private String generateHandleFromTitle(String title, String channelId) {
        if (title == null || title.isBlank() || title.equals("Unknown")) {
            // Use shortened channel ID as last resort
            if (channelId != null && channelId.length() > 8) {
                return "@" + channelId.substring(2, 10).toLowerCase();
            }
            return "@unknown" + System.currentTimeMillis() % 10000;
        }

        // Clean the title and make it handle-friendly
        String cleanTitle = title
                .replaceAll("[^a-zA-Z0-9\\s]", "") // Remove special chars except spaces
                .trim()
                .replaceAll("\\s+", "") // Remove all spaces
                .toLowerCase();

        if (cleanTitle.length() > 3 && cleanTitle.length() <= 20) {
            return "@" + cleanTitle;
        } else if (cleanTitle.length() > 20) {
            return "@" + cleanTitle.substring(0, 20);
        } else if (cleanTitle.length() > 0) {
            // Title too short, pad with channel ID
            String shortId = channelId != null && channelId.length() > 8
                    ? channelId.substring(2, 6).toLowerCase()
                    : "1234";
            return "@" + cleanTitle + shortId;
        }

        // Fallback to channel ID
        if (channelId != null && channelId.length() > 8) {
            return "@" + channelId.substring(2, 10).toLowerCase();
        }

        return "@unknown" + System.currentTimeMillis() % 10000;
    }

    // Database cleanup operations - NEW
    @PostMapping("/cleanup-duplicates")
    @ResponseBody
    public String cleanupDuplicates() {
        try {
            // Find potential duplicates by platform and handle
            var channels = channelRepo.findAll();
            int duplicatesFound = 0;
            int duplicatesRemoved = 0;

            var channelsByPlatformAndHandle = channels.stream()
                    .collect(java.util.stream.Collectors.groupingBy(
                            c -> c.getPlatform() + ":" + c.getHandle().toLowerCase()
                    ));

            for (var entry : channelsByPlatformAndHandle.entrySet()) {
                var duplicateList = entry.getValue();
                if (duplicateList.size() > 1) {
                    duplicatesFound += duplicateList.size() - 1;

                    // Keep the one with the most recent data, remove others
                    var toKeep = duplicateList.stream()
                            .max((a, b) -> {
                                var aStats = statRepo.findTopByChannelIdOrderBySnapshotDateDesc(a.getId());
                                var bStats = statRepo.findTopByChannelIdOrderBySnapshotDateDesc(b.getId());
                                if (aStats.isEmpty() && bStats.isEmpty()) return 0;
                                if (aStats.isEmpty()) return -1;
                                if (bStats.isEmpty()) return 1;
                                return aStats.get().getSnapshotDate().compareTo(bStats.get().getSnapshotDate());
                            })
                            .orElse(duplicateList.get(0));

                    for (var duplicate : duplicateList) {
                        if (!duplicate.getId().equals(toKeep.getId())) {
                            channelRepo.delete(duplicate);
                            duplicatesRemoved++;
                        }
                    }
                }
            }

            return String.format("🧹 Cleanup completed! Found %d duplicates, removed %d",
                    duplicatesFound, duplicatesRemoved);
        } catch (Exception e) {
            return "❌ Error during cleanup: " + e.getMessage();
        }
    }

    // Debug database issues - NEW
    @GetMapping("/debug-db")
    @ResponseBody
    public String debugDb() {
        try {
            var channels = channelRepo.findAll();
            long totalChannels = channels.size();

            long channelsWithoutHandle = channels.stream()
                    .filter(c -> c.getHandle() == null || c.getHandle().isBlank())
                    .count();

            long channelsWithoutTitle = channels.stream()
                    .filter(c -> c.getTitle() == null || c.getTitle().isBlank())
                    .count();

            long channelsWithoutAvatar = channels.stream()
                    .filter(c -> c.getAvatarUrl() == null || c.getAvatarUrl().isBlank())
                    .count();

            var platformCounts = channels.stream()
                    .collect(java.util.stream.Collectors.groupingBy(
                            Channel::getPlatform,
                            java.util.stream.Collectors.counting()
                    ));

            StringBuilder sb = new StringBuilder("🔍 Database Debug Report:\n\n");
            sb.append(String.format("📊 Total Channels: %d\n", totalChannels));
            sb.append("📱 By Platform:\n");
            for (var entry : platformCounts.entrySet()) {
                sb.append(String.format("   • %s: %d\n", entry.getKey(), entry.getValue()));
            }

            sb.append("\n⚠️ Data Quality Issues:\n");
            sb.append(String.format("   • Missing handles: %d\n", channelsWithoutHandle));
            sb.append(String.format("   • Missing titles: %d\n", channelsWithoutTitle));
            sb.append(String.format("   • Missing avatars: %d\n", channelsWithoutAvatar));

            // Sample of recent channels
            sb.append("\n📝 Recent Channels (last 10):\n");
            channels.stream()
                    .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                    .limit(10)
                    .forEach(c -> sb.append(String.format("   • %s (%s) - %s\n",
                            c.getTitle(), c.getHandle(), c.getPlatform())));

            return sb.toString();
        } catch (Exception e) {
            return "❌ Debug failed: " + e.getMessage();
        }
    }

    // System status overview - NEW
    @GetMapping("/system-status")
    @ResponseBody
    public String systemStatus() {
        try {
            long totalChannels = channelRepo.count();
            long totalStats = statRepo.count();

            // Check if rapid discovery mode is active
            boolean rapidMode = totalChannels < 1000;

            // Recent activity
            long recentStats = statRepo.findAll().stream()
                    .filter(s -> s.getSnapshotDate().isAfter(LocalDate.now().minusDays(7)))
                    .count();

            return String.format(
                    """
                    🖥️ System Status Report:
                    
                    📊 Database:
                       • Total Channels: %d
                       • Total Stats: %d
                       • Recent Stats (7 days): %d
                    
                    🤖 Discovery System:
                       • Mode: %s
                       • Multi-threading: ✅ Enabled
                       • Async processing: ✅ Active
                       • Next scheduled run: %s
                    
                    🚀 Performance:
                       • Database size: %s
                       • Discovery efficiency: %.1f stats per channel
                       • Thread pools: Discovery (4-8), Snapshot (2-4)
                    
                    💡 Recommendations:
                    %s
                    """,
                    totalChannels, totalStats, recentStats,
                    rapidMode ? "🚀 Rapid Mode (every 15 min)" : "📅 Daily Mode (3 AM UTC)",
                    rapidMode ? "Next 15-minute interval" : "Tomorrow 3 AM UTC",
                    totalChannels < 100 ? "Small" : totalChannels < 1000 ? "Medium" : "Large",
                    totalChannels > 0 ? (double) totalStats / totalChannels : 0,
                    getSystemRecommendations(totalChannels, totalStats, rapidMode)
            );
        } catch (Exception e) {
            return "❌ Error getting system status: " + e.getMessage();
        }
    }

    private String getSystemRecommendations(long channels, long stats, boolean rapidMode) {
        if (channels == 0) {
            return "🌱 Start by running 'Seed Popular' to bootstrap your database!";
        } else if (channels < 50) {
            return "🚀 Run 'Mass Discovery' to quickly expand your channel database with parallel processing!";
        } else if (rapidMode) {
            return "⚡ Rapid mode active - database will grow automatically every 15 minutes with multi-threading!";
        } else if (stats < channels * 5) {
            return "📊 Consider running 'Batch Snapshot' to collect more historical data in parallel!";
        } else {
            return "✅ System running optimally with multi-threaded processing! Monitor health check regularly.";
        }
    }

    // Simple admin page
    @GetMapping("")
    public String adminPage() {
        return "admin";
    }
}