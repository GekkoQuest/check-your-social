package quest.gekko.cys.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import quest.gekko.cys.domain.Channel;
import quest.gekko.cys.domain.Platform;
import quest.gekko.cys.repo.ChannelRepo;
import quest.gekko.cys.repo.DailyStatRepo;
import quest.gekko.cys.service.connector.PlatformConnector;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SmartDiscoveryService {

    private final Map<Platform, PlatformConnector> connectorsByPlatform;
    private final ChannelService channelService;
    private final ChannelRepo channelRepo;
    private final DailyStatRepo statRepo;
    private final StatsService statsService;

    // Massively expanded discovery queries for rapid population
    private final List<DiscoveryCategory> DISCOVERY_CATEGORIES = List.of(
            // Tech & Programming (expanded)
            new DiscoveryCategory("Tech Reviews", List.of(
                    "smartphone review", "laptop review", "tech unboxing", "gadget review",
                    "apple review", "samsung review", "google pixel", "oneplus review",
                    "gaming laptop", "macbook review", "iphone review", "android review",
                    "tech news", "ces 2024", "tech trends", "best phone", "tech tips",
                    "wireless earbuds", "smartwatch review", "tablet review", "camera review"
            )),

            new DiscoveryCategory("Programming", List.of(
                    "programming tutorial", "coding", "python tutorial", "javascript",
                    "react tutorial", "web development", "software development",
                    "machine learning", "ai tutorial", "data science", "cybersecurity",
                    "devops", "cloud computing", "aws tutorial", "docker tutorial",
                    "kubernetes", "nodejs", "java tutorial", "c++ programming"
            )),

            // Gaming (massive category)
            new DiscoveryCategory("Gaming", List.of(
                    "minecraft", "fortnite", "valorant", "league of legends", "apex legends",
                    "call of duty", "fifa", "gta", "among us", "fall guys", "rocket league",
                    "overwatch", "cs go", "dota 2", "world of warcraft", "roblox",
                    "pokemon", "zelda", "mario", "nintendo", "xbox", "playstation",
                    "gaming news", "game review", "gaming tips", "speedrun", "esports",
                    "twitch highlights", "gaming montage", "let's play", "horror games"
            )),

            // Education & Learning
            new DiscoveryCategory("Education", List.of(
                    "math tutorial", "physics", "chemistry", "biology", "history",
                    "language learning", "english", "spanish", "french", "chinese",
                    "online course", "khan academy", "crash course", "ted talk",
                    "science experiment", "documentary", "how to", "diy tutorial",
                    "study tips", "exam preparation", "college prep", "homework help"
            )),

            // Entertainment & Lifestyle
            new DiscoveryCategory("Entertainment", List.of(
                    "comedy", "funny videos", "pranks", "reaction", "memes",
                    "music", "cover song", "original song", "music production",
                    "movie review", "tv show", "netflix", "disney", "marvel",
                    "anime", "manga", "k-pop", "pop music", "rock music",
                    "stand up comedy", "sketch comedy", "parody", "viral videos"
            )),

            new DiscoveryCategory("Lifestyle", List.of(
                    "vlog", "daily vlog", "travel", "travel vlog", "food",
                    "cooking", "recipe", "baking", "restaurant review",
                    "fashion", "beauty", "makeup", "skincare", "hairstyle",
                    "fitness", "workout", "yoga", "meditation", "health",
                    "home decor", "interior design", "organization", "minimalism"
            )),

            // Business & Finance
            new DiscoveryCategory("Business", List.of(
                    "business", "entrepreneurship", "startup", "investing",
                    "stock market", "cryptocurrency", "bitcoin", "trading",
                    "real estate", "marketing", "personal finance", "money",
                    "economics", "passive income", "side hustle", "freelancing",
                    "dropshipping", "affiliate marketing", "digital marketing"
            )),

            // News & Commentary
            new DiscoveryCategory("News", List.of(
                    "news", "politics", "current events", "breaking news",
                    "world news", "local news", "commentary", "analysis",
                    "debate", "opinion", "journalism", "interview",
                    "political commentary", "news analysis", "fact check"
            )),

            // Hobbies & Interests
            new DiscoveryCategory("Hobbies", List.of(
                    "photography", "art", "drawing", "painting", "crafts",
                    "gardening", "plants", "pets", "dogs", "cats",
                    "cars", "automotive", "motorcycles", "aviation",
                    "sports", "football", "basketball", "soccer", "tennis",
                    "fishing", "camping", "hiking", "woodworking", "model building"
            ))
    );

    private int currentQueryIndex = 0;

    /**
     * Rapid discovery mode - runs more frequently during bootstrap phase
     */
    @Scheduled(cron = "0 */15 * * * *", zone = "UTC") // Every 15 minutes during bootstrap
    public void rapidDiscoveryMode() {
        if (shouldRunRapidMode()) {
            runParallelDiscovery();
        }
    }

    /**
     * Regular daily discovery - runs once database is populated
     */
    @Scheduled(cron = "0 0 3 * * *", zone = "UTC")
    public void dailyDiscovery() {
        if (!shouldRunRapidMode()) {
            runStandardDiscovery();
        }
    }

    private boolean shouldRunRapidMode() {
        long totalChannels = channelRepo.count();
        // Run rapid mode until we have at least 1000 channels
        return totalChannels < 1000;
    }

    public void runParallelDiscovery() {
        var youtubeConnector = connectorsByPlatform.get(Platform.YOUTUBE);
        if (youtubeConnector == null) return;

        System.out.println("🚀 Running rapid discovery mode...");

        // Get random categories to search
        List<DiscoveryCategory> categories = getRandomCategories(3);

        for (DiscoveryCategory category : categories) {
            // Search multiple terms from each category
            List<String> terms = getRandomTerms(category, 5);

            for (String term : terms) {
                try {
                    discoverChannelsForTerm(youtubeConnector, term, 25); // More results per search
                    Thread.sleep(100); // Minimal delay
                } catch (Exception e) {
                    System.err.println("Error in rapid discovery for " + term + ": " + e.getMessage());
                }
            }
        }

        System.out.println("✅ Rapid discovery completed. Total channels: " + channelRepo.count());
    }

    private void runStandardDiscovery() {
        var youtubeConnector = connectorsByPlatform.get(Platform.YOUTUBE);
        if (youtubeConnector == null) return;

        // Use a different search term each day to avoid repetition
        DiscoveryCategory todaysCategory = DISCOVERY_CATEGORIES.get(currentQueryIndex % DISCOVERY_CATEGORIES.size());
        String todaysQuery = getRandomTerms(todaysCategory, 1).get(0);
        currentQueryIndex++;

        try {
            System.out.println("🔍 Daily discovery starting: " + todaysQuery);
            discoverChannelsForTerm(youtubeConnector, todaysQuery, 20);
            System.out.println("✅ Daily discovery completed for: " + todaysQuery);
        } catch (Exception e) {
            System.err.println("❌ Daily discovery failed for '" + todaysQuery + "': " + e.getMessage());
        }
    }

    /**
     * Bootstrap method to quickly populate with high-quality channels
     */
    public void seedPopularChannels() {
        var youtubeConnector = connectorsByPlatform.get(Platform.YOUTUBE);
        if (youtubeConnector == null) return;

        // Curated list of proven popular channels across niches
        List<String> popularHandles = List.of(
                // Tech
                "@mkbhd", "@unboxtherapy", "@ijustine", "@dave2d", "@austin",
                "@techlinked", "@gamersNexus", "@jayztwocents", "@linustechtips",

                // Education
                "@veritasium", "@3blue1brown", "@khanacademy", "@crashcourse",
                "@tedx", "@vsauce", "@scishow", "@minutephysics", "@numberphile",

                // Gaming
                "@pewdiepie", "@mrbeast", "@jacksepticeye", "@markiplier",
                "@ninja", "@tfue", "@pokimane", "@shroud", "@summit1g",

                // Entertainment
                "@comedycentral", "@snl", "@thetonightshow", "@lastweektonight",
                "@collegehumor", "@smosh", "@goodmythicalmorning",

                // Music
                "@taylorswift", "@justinbieber", "@arianagrande", "@edsheeran",
                "@billboard", "@vevo", "@spinnin", "@trapnation",

                // Business/Finance
                "@cnbc", "@bloomberg", "@grahamstephan", "@andrei_jikh",
                "@meetkevin", "@biggerpockets"
        );

        System.out.println("🌱 Bootstrapping with popular channels...");
        int added = 0;

        for (String handle : popularHandles) {
            try {
                var channelOpt = youtubeConnector.resolveAndHydrate(handle);
                if (channelOpt.isPresent()) {
                    channelService.upsertChannel(channelOpt.get());
                    added++;
                    System.out.println("✓ Added: " + handle);
                } else {
                    System.out.println("✗ Not found: " + handle);
                }
                Thread.sleep(200); // Be respectful to API
            } catch (Exception e) {
                System.err.println("Error adding " + handle + ": " + e.getMessage());
            }
        }

        System.out.println("🎉 Bootstrap completed. Added " + added + " popular channels.");
    }

    /**
     * Enhanced search with trending and recommendation discovery
     */
    public void discoverTrendingChannels() {
        var youtubeConnector = connectorsByPlatform.get(Platform.YOUTUBE);
        if (youtubeConnector == null) return;

        // Search for trending topics to find currently popular channels
        List<String> trendingQueries = List.of(
                "viral", "trending", "popular", "2024 best", "top 10",
                "million views", "breaking", "latest", "new channel",
                "rising star", "up and coming", "small youtuber"
        );

        System.out.println("🔥 Discovering trending channels...");
        for (String query : trendingQueries) {
            try {
                discoverChannelsForTerm(youtubeConnector, query, 20);
                Thread.sleep(500); // Slightly longer delay for trending searches
            } catch (Exception e) {
                System.err.println("Error discovering trending for " + query + ": " + e.getMessage());
            }
        }
        System.out.println("✅ Trending discovery completed!");
    }

    /**
     * Discover channels by exploring related channels (network effect)
     */
    public void discoverRelatedChannels() {
        // Get a sample of existing channels and search for related content
        List<Channel> existingChannels = channelRepo.findAll().stream()
                .limit(50)
                .collect(Collectors.toList());

        var youtubeConnector = connectorsByPlatform.get(Platform.YOUTUBE);
        if (youtubeConnector == null) return;

        System.out.println("🔗 Discovering related channels...");
        for (Channel channel : existingChannels) {
            try {
                // Search for channels similar to existing ones
                String[] searchTerms = {
                        "like " + channel.getTitle(),
                        channel.getTitle() + " similar",
                        "channels like " + channel.getHandle()
                };

                for (String term : searchTerms) {
                    var foundChannels = youtubeConnector.search(term, 10);
                    for (var found : foundChannels) {
                        channelService.upsertChannelIdentityOnly(found);
                    }
                    Thread.sleep(200);
                }
            } catch (Exception e) {
                System.err.println("Error discovering related to " + channel.getTitle() + ": " + e.getMessage());
            }
        }
        System.out.println("✅ Related channel discovery completed!");
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

    private void discoverChannelsForTerm(PlatformConnector connector, String term, int maxResults) {
        try {
            var channels = connector.search(term, maxResults);
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
                System.out.println("  📈 " + term + ": " + discovered + " new channels");
            }
        } catch (Exception e) {
            System.err.println("Error discovering for " + term + ": " + e.getMessage());
        }
    }

    private List<DiscoveryCategory> getRandomCategories(int count) {
        List<DiscoveryCategory> shuffled = new ArrayList<>(DISCOVERY_CATEGORIES);
        Collections.shuffle(shuffled);
        return shuffled.stream().limit(count).collect(Collectors.toList());
    }

    private List<String> getRandomTerms(DiscoveryCategory category, int count) {
        List<String> terms = new ArrayList<>(category.terms());
        Collections.shuffle(terms);
        return terms.stream().limit(count).collect(Collectors.toList());
    }

    /**
     * Manual discovery trigger for administrators
     */
    public String manualDiscovery() {
        try {
            runParallelDiscovery();
            return "Manual discovery completed successfully";
        } catch (Exception e) {
            return "Manual discovery failed: " + e.getMessage();
        }
    }

    /**
     * Mass discovery - runs all discovery methods
     */
    public String triggerMassDiscovery() {
        try {
            System.out.println("🚀 Starting mass discovery...");

            seedPopularChannels();
            Thread.sleep(2000); // Brief pause

            discoverTrendingChannels();
            Thread.sleep(2000);

            runParallelDiscovery();
            Thread.sleep(2000);

            discoverRelatedChannels();

            long total = channelRepo.count();
            String result = "✅ Mass discovery completed! Total channels: " + total;
            System.out.println(result);
            return result;
        } catch (Exception e) {
            String error = "❌ Mass discovery failed: " + e.getMessage();
            System.err.println(error);
            return error;
        }
    }

    /**
     * Batch snapshot channels that need data
     */
    public String batchSnapshot(int limit) {
        try {
            List<Channel> channels = channelRepo.findAll().stream()
                    .filter(c -> {
                        var latestStat = statRepo.findTopByChannelIdOrderBySnapshotDateDesc(c.getId());
                        return latestStat.isEmpty() ||
                                latestStat.get().getSnapshotDate().isBefore(LocalDate.now().minusDays(1));
                    })
                    .limit(limit)
                    .collect(Collectors.toList());

            var youtubeConnector = connectorsByPlatform.get(Platform.YOUTUBE);
            if (youtubeConnector == null) return "No YouTube connector available";

            int processed = 0;
            for (Channel channel : channels) {
                try {
                    if (channel.getPlatform() == Platform.YOUTUBE) {
                        var counters = youtubeConnector.fetchCounters(channel.getPlatformId());
                        statsService.snapshot(channel, counters, LocalDate.now());
                        processed++;
                        Thread.sleep(500); // Rate limiting
                    }
                } catch (Exception e) {
                    System.err.println("Error processing " + channel.getHandle() + ": " + e.getMessage());
                }
            }

            return String.format("✅ Batch snapshot completed! Processed %d/%d channels",
                    processed, channels.size());
        } catch (Exception e) {
            return "Batch snapshot failed: " + e.getMessage();
        }
    }

    /**
     * Get discovery statistics
     */
    public DiscoveryStats getDiscoveryProgress() {
        long totalChannels = channelRepo.count();
        long channelsWithStats = statRepo.findAll().stream()
                .map(s -> s.getChannel().getId())
                .distinct()
                .count();

        return new DiscoveryStats(
                (int) totalChannels,
                currentQueryIndex,
                DISCOVERY_CATEGORIES.size() * 10,
                DISCOVERY_CATEGORIES.get((currentQueryIndex - 1 + DISCOVERY_CATEGORIES.size()) % DISCOVERY_CATEGORIES.size()).name()
        );
    }

    /**
     * Get enhanced statistics
     */
    public String getEnhancedStats() {
        try {
            long totalChannels = channelRepo.count();
            long channelsWithStats = statRepo.findAll().stream()
                    .map(s -> s.getChannel().getId())
                    .distinct()
                    .count();

            long youtubeChannels = channelRepo.findAll().stream()
                    .filter(c -> c.getPlatform() == Platform.YOUTUBE)
                    .count();

            long twitchChannels = channelRepo.findAll().stream()
                    .filter(c -> c.getPlatform() == Platform.TWITCH)
                    .count();

            return String.format(
                    """
                    📊 Smart Discovery Statistics:
                    
                    📈 Total Channels: %d
                       • YouTube: %d
                       • Twitch: %d
                    
                    📊 Channels with Stats: %d (%.1f%%)
                    
                    🎯 Discovery Progress:
                       • Target for rapid mode: 1,000 channels
                       • Current progress: %.1f%%
                       • Discovery mode: %s
                    
                    🔧 Available Actions:
                       • Seed Popular: Add 50+ popular channels instantly
                       • Mass Discovery: Run all discovery methods
                       • Trending: Find currently popular channels
                       • Related: Discover channels similar to existing ones
                    
                    💡 Recommendation: %s
                    """,
                    totalChannels, youtubeChannels, twitchChannels,
                    channelsWithStats,
                    totalChannels > 0 ? (channelsWithStats * 100.0 / totalChannels) : 0,
                    totalChannels > 0 ? (totalChannels * 100.0 / 1000) : 0,
                    shouldRunRapidMode() ? "🚀 Rapid Mode (every 15 min)" : "📅 Daily Mode",
                    getRecommendation(totalChannels)
            );
        } catch (Exception e) {
            return "Error getting stats: " + e.getMessage();
        }
    }

    private String getRecommendation(long totalChannels) {
        if (totalChannels < 50) {
            return "🚀 Start with 'Seed Popular' to add popular channels instantly!";
        } else if (totalChannels < 200) {
            return "📈 Run 'Mass Discovery' to rapidly expand your database!";
        } else if (totalChannels < 500) {
            return "🎯 Use 'Trending Discovery' to find currently popular channels!";
        } else if (totalChannels < 1000) {
            return "🔍 Try 'Related Discovery' to find similar channels!";
        } else {
            return "✅ Great! You have a solid database. Focus on regular snapshots.";
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
            this.progressPercentage = totalQueries > 0 ? ((double) queriesCompleted / totalQueries) * 100 : 0;
        }
    }

    private record DiscoveryCategory(String name, List<String> terms) {}
}