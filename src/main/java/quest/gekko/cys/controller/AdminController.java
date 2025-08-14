package quest.gekko.cys.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import quest.gekko.cys.domain.Channel;
import quest.gekko.cys.domain.Platform;
import quest.gekko.cys.repo.ChannelRepo;
import quest.gekko.cys.repo.DailyStatRepo;
import quest.gekko.cys.service.RankingService;
import quest.gekko.cys.service.SmartDiscoveryService;
import quest.gekko.cys.service.StatsService;
import quest.gekko.cys.service.connector.PlatformConnector;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final List<PlatformConnector> connectors;
    private final ChannelRepo channelRepo;
    private final StatsService statsService;
    private final RankingService rankingService;
    private final DailyStatRepo statRepo;
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

    // Manual snapshot for ALL channels
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

    // Seed popular channels
    @PostMapping("/seed-popular")
    @ResponseBody
    public String seedPopularChannels() {
        try {
            smartDiscoveryService.seedPopularChannels();
            return "OK: Popular channels seeded successfully";
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    // Manual discovery trigger
    @PostMapping("/discover-now")
    @ResponseBody
    public String discoverNow() {
        try {
            return smartDiscoveryService.manualDiscovery();
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    // Mass discovery - NEW powerful discovery method
    @PostMapping("/mass-discovery")
    @ResponseBody
    public String massDiscovery() {
        try {
            return smartDiscoveryService.triggerMassDiscovery();
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    // Trending discovery - NEW
    @PostMapping("/discover-trending")
    @ResponseBody
    public String discoverTrending() {
        try {
            smartDiscoveryService.discoverTrendingChannels();
            return "✅ Trending discovery completed!";
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    // Related channels discovery - NEW
    @PostMapping("/discover-related")
    @ResponseBody
    public String discoverRelated() {
        try {
            smartDiscoveryService.discoverRelatedChannels();
            return "✅ Related channels discovery completed!";
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    // Batch snapshot for channels without recent data - NEW
    @PostMapping("/batch-snapshot")
    @ResponseBody
    public String batchSnapshot(@RequestParam(defaultValue = "100") int limit) {
        try {
            return smartDiscoveryService.batchSnapshot(limit);
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    // Get discovery statistics - ENHANCED
    @GetMapping("/discovery-stats")
    @ResponseBody
    public String getDiscoveryStats() {
        try {
            return smartDiscoveryService.getEnhancedStats();
        } catch (Exception e) {
            return "Error getting stats: " + e.getMessage();
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
                    
                    %s
                    """,
                    totalChannels, youtubeChannels, twitchChannels, activeChannels, staleChannels,
                    totalChannels > 0 ? (activeChannels * 100.0 / totalChannels) : 0,
                    staleChannels > 0 ? "💡 Consider running batch snapshot to refresh stale channels" : "✅ All channels are up to date!"
            );
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    // Discover channels by search term
    @PostMapping("/discover")
    @ResponseBody
    public String discoverChannels(@RequestParam String searchTerm,
                                   @RequestParam(defaultValue = "10") int maxResults) {
        try {
            smartDiscoveryService.opportunisticDiscovery(searchTerm);
            return "OK: Discovered channels for '" + searchTerm + "'";
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
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
            return "Error during cleanup: " + e.getMessage();
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
            return "Debug failed: " + e.getMessage();
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
                       • Next scheduled run: %s
                    
                    🚀 Performance:
                       • Database size: %s
                       • Discovery efficiency: %.1f stats per channel
                    
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
            return "Error getting system status: " + e.getMessage();
        }
    }

    private String getSystemRecommendations(long channels, long stats, boolean rapidMode) {
        if (channels == 0) {
            return "🌱 Start by running 'Seed Popular' to bootstrap your database!";
        } else if (channels < 50) {
            return "🚀 Run 'Mass Discovery' to quickly expand your channel database!";
        } else if (rapidMode) {
            return "⚡ Rapid mode active - database will grow automatically every 15 minutes!";
        } else if (stats < channels * 5) {
            return "📊 Consider running 'Batch Snapshot' to collect more historical data!";
        } else {
            return "✅ System running optimally! Monitor health check regularly.";
        }
    }

    // Simple admin page
    @GetMapping("")
    public String adminPage() {
        return "admin";
    }
}