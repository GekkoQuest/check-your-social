package quest.gekko.cys.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import quest.gekko.cys.domain.Platform;
import quest.gekko.cys.repo.ChannelRepo;
import quest.gekko.cys.service.connector.PlatformConnector;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SmartDiscoveryService {

    private final Map<Platform, PlatformConnector> connectorsByPlatform;
    private final ChannelService channelService;
    private final ChannelRepo channelRepo;

    // Strategic niches to build comprehensive coverage
    private final List<String> DISCOVERY_QUERIES = List.of(
            // Tech & Programming
            "tech review", "smartphone review", "laptop review", "gadget unboxing",
            "programming tutorial", "coding", "software development", "ai tutorial",
            "cybersecurity", "web development", "mobile development",

            // Gaming
            "gaming", "minecraft", "fortnite", "valorant", "league of legends",
            "game review", "speedrun", "esports", "indie games", "retro gaming",

            // Education & Science
            "tutorial", "how to", "learning", "science", "math", "physics",
            "chemistry", "biology", "history", "language learning", "online course",
            "educational", "documentary",

            // Entertainment & Media
            "comedy", "music", "cover song", "reaction", "podcast", "animation",
            "movie review", "tv show", "film analysis", "music production",

            // Lifestyle & Health
            "cooking", "recipe", "fitness", "workout", "yoga", "meditation",
            "travel", "vlog", "fashion", "beauty", "makeup tutorial", "skincare",

            // Business & Finance
            "business", "entrepreneurship", "investing", "stock market", "crypto",
            "real estate", "marketing", "personal finance", "economics",

            // News & Commentary
            "news", "politics", "current events", "commentary", "analysis",
            "journalism", "debate", "opinion"
    );

    private int currentQueryIndex = 0;

    /**
     * Discover channels systematically - runs daily at 3 AM UTC
     * This approach can legally discover 1000s of channels over time
     */
    @Scheduled(cron = "0 0 3 * * *", zone = "UTC")
    public void dailyDiscovery() {
        var youtubeConnector = connectorsByPlatform.get(Platform.YOUTUBE);
        if (youtubeConnector == null) {
            System.err.println("YouTube connector not available for daily discovery");
            return;
        }

        // Use a different search term each day to avoid repetition
        String todaysQuery = DISCOVERY_QUERIES.get(currentQueryIndex % DISCOVERY_QUERIES.size());
        currentQueryIndex++;

        try {
            System.out.println("🔍 Daily discovery starting: " + todaysQuery);

            // Discover channels in this niche
            var channels = youtubeConnector.search(todaysQuery, 20);
            int newChannels = 0;
            int existingChannels = 0;

            for (var channel : channels) {
                try {
                    // Check if we already have this channel
                    var existing = channelRepo.findByPlatformAndPlatformId(
                            channel.getPlatform(),
                            channel.getPlatformId()
                    );

                    if (existing.isEmpty()) {
                        // New channel - store identity only for now
                        channelService.upsertChannelIdentityOnly(channel);
                        newChannels++;
                        System.out.println("  ➕ New: " + channel.getTitle());
                    } else {
                        existingChannels++;
                    }

                    // Be nice to API - small delay between requests
                    Thread.sleep(100);

                } catch (Exception e) {
                    System.err.println("  ❌ Error saving channel " + channel.getTitle() + ": " + e.getMessage());
                }
            }

            System.out.println("✅ Daily discovery completed for: " + todaysQuery);
            System.out.println("   📊 Results: " + newChannels + " new, " + existingChannels + " existing");

        } catch (Exception e) {
            System.err.println("❌ Daily discovery failed for '" + todaysQuery + "': " + e.getMessage());
        }
    }

    /**
     * Manual discovery trigger for testing
     */
    public String manualDiscovery() {
        try {
            dailyDiscovery();
            return "Manual discovery completed successfully";
        } catch (Exception e) {
            return "Manual discovery failed: " + e.getMessage();
        }
    }

    /**
     * Discover channels when a user searches but doesn't find what they want
     */
    public void opportunisticDiscovery(String userSearchTerm) {
        var youtubeConnector = connectorsByPlatform.get(Platform.YOUTUBE);
        if (youtubeConnector == null) return;

        try {
            System.out.println("🎯 Opportunistic discovery for: " + userSearchTerm);

            // If user searched for something we don't have, discover it
            var channels = youtubeConnector.search(userSearchTerm, 10);
            int discovered = 0;

            for (var channel : channels) {
                var existing = channelRepo.findByPlatformAndPlatformId(
                        channel.getPlatform(),
                        channel.getPlatformId()
                );

                if (existing.isEmpty()) {
                    channelService.upsertChannelIdentityOnly(channel);
                    discovered++;
                }
            }

            if (discovered > 0) {
                System.out.println("   📈 Discovered " + discovered + " new channels");
            }

        } catch (Exception e) {
            System.err.println("Opportunistic discovery failed: " + e.getMessage());
        }
    }

    /**
     * Get discovery statistics
     */
    public DiscoveryStats getDiscoveryProgress() {
        long totalChannels = channelRepo.count();

        return new DiscoveryStats(
                (int) totalChannels,
                currentQueryIndex,
                DISCOVERY_QUERIES.size(),
                DISCOVERY_QUERIES.get((currentQueryIndex - 1) % DISCOVERY_QUERIES.size())
        );
    }

    /**
     * Seed with popular channels for initial bootstrap
     */
    public void seedPopularChannels() {
        List<String> popularChannels = List.of(
                "@mkbhd", "@veritasium", "@3blue1brown", "@techlinked",
                "@unboxtherapy", "@austin", "@ijustine", "@dave2d",
                "@computerphile", "@numberphile", "@engineerguy",
                "@smarter", "@tomscott", "@khanacademy", "@crashcourse",
                "@pewdiepie", "@mrbeast", "@dude", "@jacksepticeye",
                "@markiplier", "@gametheory", "@vsauce", "@scishow"
        );

        var youtubeConnector = connectorsByPlatform.get(Platform.YOUTUBE);
        if (youtubeConnector == null) return;

        for (String handle : popularChannels) {
            try {
                var channelOpt = youtubeConnector.resolveAndHydrate(handle);
                if (channelOpt.isPresent()) {
                    channelService.upsertChannel(channelOpt.get());
                    System.out.println("✓ Seeded: " + handle);
                } else {
                    System.out.println("✗ Not found: " + handle);
                }

                Thread.sleep(200); // Be nice to the API

            } catch (Exception e) {
                System.err.println("Error seeding " + handle + ": " + e.getMessage());
            }
        }
    }

    public static class DiscoveryStats {
        public final int totalChannels;
        public final int queriesCompleted;
        public final int totalQueries;
        public final String lastQuery;
        public final double progressPercentage;

        public DiscoveryStats(int totalChannels, int queriesCompleted, int totalQueries, String lastQuery) {
            this.totalChannels = totalChannels;
            this.queriesCompleted = queriesCompleted;
            this.totalQueries = totalQueries;
            this.lastQuery = lastQuery;
            this.progressPercentage = ((double) queriesCompleted / totalQueries) * 100;
        }
    }
}