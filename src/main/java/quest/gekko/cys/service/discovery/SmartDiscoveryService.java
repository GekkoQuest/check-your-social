package quest.gekko.cys.service.discovery;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import quest.gekko.cys.domain.Channel;
import quest.gekko.cys.domain.Platform;
import quest.gekko.cys.repository.ChannelRepository;
import quest.gekko.cys.repository.DailyStatRepository;
import quest.gekko.cys.service.integration.connector.PlatformConnector;
import quest.gekko.cys.service.core.ChannelService;
import quest.gekko.cys.service.core.StatsService;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SmartDiscoveryService {

    private final Map<Platform, PlatformConnector> connectorsByPlatform;
    private final ChannelService channelService;
    private final ChannelRepository channelRepository;
    private final DailyStatRepository statRepo;
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
    private final AtomicInteger discoveryProgress = new AtomicInteger(0);

    /**
     * Rapid discovery mode - runs more frequently during bootstrap phase
     */
    @Scheduled(cron = "0 */15 * * * *", zone = "UTC") // Every 15 minutes during bootstrap
    public void rapidDiscoveryMode() {
        if (shouldRunRapidMode()) {
            log.info("🚀 Triggering rapid discovery mode");
            runParallelDiscoveryAsync();
        }
    }

    /**
     * Regular daily discovery - runs once database is populated
     */
    @Scheduled(cron = "0 0 3 * * *", zone = "UTC")
    public void dailyDiscovery() {
        if (!shouldRunRapidMode()) {
            log.info("📅 Running daily discovery");
            runStandardDiscoveryAsync();
        }
    }

    private boolean shouldRunRapidMode() {
        long totalChannels = channelRepository.count();
        // Run rapid mode until we have at least 1000 channels
        return totalChannels < 1000;
    }

    @Async("discoveryExecutor")
    public CompletableFuture<Void> runParallelDiscoveryAsync() {
        return CompletableFuture.runAsync(this::runParallelDiscovery);
    }

    @Async("discoveryExecutor")
    public CompletableFuture<Void> runStandardDiscoveryAsync() {
        return CompletableFuture.runAsync(this::runStandardDiscovery);
    }

    public void runParallelDiscovery() {
        var youtubeConnector = connectorsByPlatform.get(Platform.YOUTUBE);
        if (youtubeConnector == null) return;

        log.info("🚀 Running rapid discovery mode...");

        // Get random categories to search
        List<DiscoveryCategory> categories = getRandomCategories(4);
        List<CompletableFuture<Integer>> futures = new ArrayList<>();

        for (DiscoveryCategory category : categories) {
            // Search multiple terms from each category
            List<String> terms = getRandomTerms(category, 6);

            for (String term : terms) {
                CompletableFuture<Integer> future = CompletableFuture.supplyAsync(() -> {
                    try {
                        return discoverChannelsForTerm(youtubeConnector, term, 20);
                    } catch (Exception e) {
                        log.error("Error in rapid discovery for " + term + ": " + e.getMessage());
                        return 0;
                    }
                });
                futures.add(future);
            }
        }

        // Wait for all discoveries to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenRun(() -> {
                    int totalDiscovered = futures.stream()
                            .mapToInt(f -> f.join())
                            .sum();
                    log.info("✅ Rapid discovery completed. Discovered {} new channels. Total channels: {}",
                            totalDiscovered, channelRepository.count());
                })
                .join();
    }

    private void runStandardDiscovery() {
        var youtubeConnector = connectorsByPlatform.get(Platform.YOUTUBE);
        if (youtubeConnector == null) return;

        // Use a different search term each day to avoid repetition
        DiscoveryCategory todaysCategory = DISCOVERY_CATEGORIES.get(currentQueryIndex % DISCOVERY_CATEGORIES.size());
        String todaysQuery = getRandomTerms(todaysCategory, 1).get(0);
        currentQueryIndex++;

        try {
            log.info("🔍 Daily discovery starting: " + todaysQuery);
            int discovered = discoverChannelsForTerm(youtubeConnector, todaysQuery, 25);
            log.info("✅ Daily discovery completed for: {}. Discovered {} channels", todaysQuery, discovered);
        } catch (Exception e) {
            log.error("❌ Daily discovery failed for '{}': {}", todaysQuery, e.getMessage());
        }
    }

    /**
     * Bootstrap method to quickly populate with high-quality channels
     */
    @Async("discoveryExecutor")
    public CompletableFuture<String> seedPopularChannelsAsync() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                seedPopularChannels();
                return "✅ Popular channels seeded successfully";
            } catch (Exception e) {
                return "❌ Error seeding popular channels: " + e.getMessage();
            }
        });
    }

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

        log.info("🌱 Bootstrapping with popular channels...");

        // Process in parallel batches
        List<CompletableFuture<String>> futures = popularHandles.stream()
                .map(handle -> CompletableFuture.supplyAsync(() -> {
                    try {
                        var channelOpt = youtubeConnector.resolveAndHydrate(handle);
                        if (channelOpt.isPresent()) {
                            channelService.upsertChannel(channelOpt.get());
                            log.debug("✓ Added: " + handle);
                            return handle + " ✓";
                        } else {
                            log.debug("✗ Not found: " + handle);
                            return handle + " ✗";
                        }
                    } catch (Exception e) {
                        log.error("Error adding " + handle + ": " + e.getMessage());
                        return handle + " ❌";
                    }
                }))
                .collect(Collectors.toList());

        // Wait for all to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        long added = futures.stream()
                .map(CompletableFuture::join)
                .filter(result -> result.contains("✓"))
                .count();

        log.info("🎉 Bootstrap completed. Added {} popular channels.", added);
    }

    /**
     * Enhanced search with trending and recommendation discovery
     */
    @Async("discoveryExecutor")
    public CompletableFuture<Void> discoverTrendingChannelsAsync() {
        return CompletableFuture.runAsync(this::discoverTrendingChannels);
    }

    public void discoverTrendingChannels() {
        var youtubeConnector = connectorsByPlatform.get(Platform.YOUTUBE);
        if (youtubeConnector == null) return;

        // Search for trending topics to find currently popular channels
        List<String> trendingQueries = List.of(
                "viral", "trending", "popular", "2024 best", "top 10",
                "million views", "breaking", "latest", "new channel",
                "rising star", "up and coming", "small youtuber"
        );

        log.info("🔥 Discovering trending channels...");

        List<CompletableFuture<Integer>> futures = trendingQueries.stream()
                .map(query -> CompletableFuture.supplyAsync(() -> {
                    try {
                        return discoverChannelsForTerm(youtubeConnector, query, 15);
                    } catch (Exception e) {
                        log.error("Error discovering trending for " + query + ": " + e.getMessage());
                        return 0;
                    }
                }))
                .collect(Collectors.toList());

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenRun(() -> {
                    int totalDiscovered = futures.stream().mapToInt(CompletableFuture::join).sum();
                    log.info("✅ Trending discovery completed! Discovered {} channels", totalDiscovered);
                })
                .join();
    }

    /**
     * Discover channels by exploring related channels (network effect)
     */
    @Async("discoveryExecutor")
    public CompletableFuture<Void> discoverRelatedChannelsAsync() {
        return CompletableFuture.runAsync(this::discoverRelatedChannels);
    }

    public void discoverRelatedChannels() {
        // Get a sample of existing channels and search for related content
        List<Channel> existingChannels = channelRepository.findAll().stream()
                .limit(30)
                .collect(Collectors.toList());

        var youtubeConnector = connectorsByPlatform.get(Platform.YOUTUBE);
        if (youtubeConnector == null) return;

        log.info("🔗 Discovering related channels...");

        List<CompletableFuture<Integer>> futures = existingChannels.stream()
                .map(channel -> CompletableFuture.supplyAsync(() -> {
                    try {
                        // Search for channels similar to existing ones
                        String[] searchTerms = {
                                "like " + channel.getTitle(),
                                channel.getTitle() + " similar"
                        };

                        int discovered = 0;
                        for (String term : searchTerms) {
                            discovered += discoverChannelsForTerm(youtubeConnector, term, 8);
                        }
                        return discovered;
                    } catch (Exception e) {
                        log.error("Error discovering related to " + channel.getTitle() + ": " + e.getMessage());
                        return 0;
                    }
                }))
                .collect(Collectors.toList());

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenRun(() -> {
                    int totalDiscovered = futures.stream().mapToInt(CompletableFuture::join).sum();
                    log.info("✅ Related channel discovery completed! Discovered {} channels", totalDiscovered);
                })
                .join();
    }

    /**
     * Discover channels when a user searches but doesn't find what they want
     */
    @Async("discoveryExecutor")
    public CompletableFuture<Void> opportunisticDiscoveryAsync(String userSearchTerm) {
        return CompletableFuture.runAsync(() -> opportunisticDiscovery(userSearchTerm));
    }

    public void opportunisticDiscovery(String userSearchTerm) {
        var youtubeConnector = connectorsByPlatform.get(Platform.YOUTUBE);
        if (youtubeConnector == null) return;

        try {
            log.info("🎯 Opportunistic discovery for: " + userSearchTerm);
            int discovered = discoverChannelsForTerm(youtubeConnector, userSearchTerm, 12);
            if (discovered > 0) {
                log.info("📈 Discovered {} new channels for search term: {}", discovered, userSearchTerm);
            }
        } catch (Exception e) {
            log.error("Opportunistic discovery failed: " + e.getMessage());
        }
    }

    private int discoverChannelsForTerm(PlatformConnector connector, String term, int maxResults) {
        try {
            var channels = connector.search(term, maxResults);
            int discovered = 0;

            for (var channel : channels) {
                var existing = channelRepository.findByPlatformAndPlatformId(
                        channel.getPlatform(),
                        channel.getPlatformId()
                );

                if (existing.isEmpty()) {
                    channelService.upsertChannelIdentityOnly(channel);
                    discovered++;
                }
            }

            if (discovered > 0) {
                log.debug("📈 {}: {} new channels", term, discovered);
            }
            return discovered;
        } catch (Exception e) {
            log.error("Error discovering for " + term + ": " + e.getMessage());
            return 0;
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
            runParallelDiscoveryAsync().join();
            return "✅ Manual discovery completed successfully";
        } catch (Exception e) {
            return "❌ Manual discovery failed: " + e.getMessage();
        }
    }

    /**
     * Mass discovery - runs all discovery methods in parallel
     */
    public String triggerMassDiscovery() {
        try {
            log.info("🚀 Starting mass discovery...");

            // Run all discovery methods in parallel
            CompletableFuture<String> seedFuture = seedPopularChannelsAsync();
            CompletableFuture<Void> trendingFuture = discoverTrendingChannelsAsync();
            CompletableFuture<Void> parallelFuture = runParallelDiscoveryAsync();
            CompletableFuture<Void> relatedFuture = discoverRelatedChannelsAsync();

            // Wait for all to complete
            CompletableFuture.allOf(seedFuture, trendingFuture, parallelFuture, relatedFuture).join();

            long total = channelRepository.count();
            String result = "✅ Mass discovery completed! Total channels: " + total;
            log.info(result);
            return result;
        } catch (Exception e) {
            String error = "❌ Mass discovery failed: " + e.getMessage();
            log.error(error, e);
            return error;
        }
    }

    /**
     * Batch snapshot channels that need data
     */
    @Async("snapshotExecutor")
    public CompletableFuture<String> batchSnapshotAsync(int limit) {
        return CompletableFuture.supplyAsync(() -> batchSnapshot(limit));
    }

    public String batchSnapshot(int limit) {
        try {
            List<Channel> channels = channelRepository.findAll().stream()
                    .filter(c -> {
                        var latestStat = statRepo.findTopByChannelIdOrderBySnapshotDateDesc(c.getId());
                        return latestStat.isEmpty() ||
                                latestStat.get().getSnapshotDate().isBefore(LocalDate.now().minusDays(1));
                    })
                    .limit(limit)
                    .collect(Collectors.toList());

            var youtubeConnector = connectorsByPlatform.get(Platform.YOUTUBE);
            if (youtubeConnector == null) return "No YouTube connector available";

            // Process snapshots in parallel batches
            List<CompletableFuture<Boolean>> futures = channels.stream()
                    .filter(c -> c.getPlatform() == Platform.YOUTUBE)
                    .map(channel -> CompletableFuture.supplyAsync(() -> {
                        try {
                            var counters = youtubeConnector.fetchCounters(channel.getPlatformId());
                            statsService.snapshot(channel, counters, LocalDate.now());
                            return true;
                        } catch (Exception e) {
                            log.error("Error processing " + channel.getHandle() + ": " + e.getMessage());
                            return false;
                        }
                    }))
                    .collect(Collectors.toList());

            // Wait for all snapshots to complete
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            long processed = futures.stream()
                    .map(CompletableFuture::join)
                    .filter(success -> success)
                    .count();

            return String.format("✅ Batch snapshot completed! Processed %d/%d channels",
                    processed, channels.size());
        } catch (Exception e) {
            return "❌ Batch snapshot failed: " + e.getMessage();
        }
    }

    // Expose async methods for controller
    public void seedPopular() {
        seedPopularChannelsAsync();
    }

    public void discoverTrending() {
        discoverTrendingChannelsAsync();
    }

    public void discoverRelated() {
        discoverRelatedChannelsAsync();
    }

    /**
     * Get enhanced statistics
     */
    public String getEnhancedStats() {
        try {
            long totalChannels = channelRepository.count();
            long channelsWithStats = statRepo.findAll().stream()
                    .map(s -> s.getChannel().getId())
                    .distinct()
                    .count();

            long youtubeChannels = channelRepository.findAll().stream()
                    .filter(c -> c.getPlatform() == Platform.YOUTUBE)
                    .count();

            long twitchChannels = channelRepository.findAll().stream()
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
                    
                    🚀 Performance:
                       • Multi-threading: ✅ Enabled
                       • Parallel discovery: ✅ Active
                       • Async snapshots: ✅ Active
                    
                    🔧 Available Actions:
                       • Seed Popular: Add 50+ popular channels instantly
                       • Mass Discovery: Run all discovery methods in parallel
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
            return "📈 Run 'Mass Discovery' to rapidly expand your database in parallel!";
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