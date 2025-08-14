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
import java.util.stream.Collectors;

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

    // Get discovery statistics
    @GetMapping("/discovery-stats")
    @ResponseBody
    public String getDiscoveryStats() {
        try {
            var stats = smartDiscoveryService.getDiscoveryProgress();
            return String.format(
                    "Discovery Progress:\n" +
                            "Total Channels: %d\n" +
                            "Queries Completed: %d/%d (%.1f%%)\n" +
                            "Last Query: %s\n" +
                            "Next automated discovery: Daily at 3 AM UTC",
                    stats.totalChannels,
                    stats.queriesCompleted,
                    stats.totalQueries,
                    stats.progressPercentage,
                    stats.lastQuery
            );
        } catch (Exception e) {
            return "Error getting stats: " + e.getMessage();
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

    // Simple admin page
    @GetMapping("")
    public String adminPage() {
        return "admin";
    }
}