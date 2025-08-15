package quest.gekko.cys.web.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import quest.gekko.cys.domain.Channel;
import quest.gekko.cys.domain.Platform;
import quest.gekko.cys.repository.ChannelRepository;
import quest.gekko.cys.service.core.ChannelService;
import quest.gekko.cys.service.discovery.SmartDiscoveryService;
import quest.gekko.cys.service.integration.connector.PlatformConnector;
import quest.gekko.cys.web.dto.ChannelWithStatsDTO;

import java.util.Map;
import java.util.Optional;

@Controller
@RequiredArgsConstructor
public class SearchController {

    private final Map<Platform, PlatformConnector> connectorsByPlatform;
    private final ChannelService channelService;
    private final ChannelRepository channelRepo;
    private final SmartDiscoveryService smartDiscoveryService;

    @GetMapping("/search")
    public String search(
            @RequestParam String q,
            @RequestParam Platform platform,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Model model
    ) {
        System.out.println("🔍 Search called with q='" + q + "', platform=" + platform);

        PlatformConnector connector = connectorsByPlatform.get(platform);
        model.addAttribute("q", q);
        model.addAttribute("platform", platform);

        if (connector == null) {
            System.out.println("❌ No connector available for platform: " + platform);
            model.addAttribute("error", "No connector available for platform: " + platform);
            model.addAttribute("page", Page.empty());
            model.addAttribute("results", Page.empty());
            return "leaderboard";
        }

        // FIRST: Always search the database for existing channels
        System.out.println("🔍 Searching existing database for: " + q);
        try {
            Page<ChannelWithStatsDTO> existingResults = channelRepo.search(q, PageRequest.of(page, size));
            System.out.println("📚 Found " + existingResults.getContent().size() + " existing channels");

            // If we have good results from database, return them
            if (!existingResults.isEmpty()) {
                model.addAttribute("page", existingResults);
                model.addAttribute("results", existingResults);
                System.out.println("✅ Returning existing database results");
                return "leaderboard";
            }
        } catch (Exception e) {
            System.out.println("❌ Database search failed: " + e.getMessage());
        }

        // SECOND: If no database results, try to resolve/discover the channel
        System.out.println("🌐 No database results, attempting to discover: " + q);

        // Check if it's a specific handle or URL that should resolve to one channel
        if (q.startsWith("@") || q.startsWith("http") || q.contains("youtube.com/") || q.contains("youtu.be/")) {
            System.out.println("🎯 Attempting to resolve specific channel: " + q);
            try {
                Optional<Channel> discovered = connector.resolveAndHydrate(q);
                if (discovered.isPresent()) {
                    // Save the discovered channel
                    Channel saved = channelService.upsertChannel(discovered.get());
                    System.out.println("✅ Channel discovered and saved, redirecting to: /channel/" + saved.getId());
                    return "redirect:/channel/" + saved.getId();
                } else {
                    System.out.println("❌ Channel not found via resolveAndHydrate for: " + q);
                }
            } catch (Exception e) {
                System.out.println("❌ Error resolving channel: " + e.getMessage());
                e.printStackTrace();
            }
        }

        // THIRD: Try broader search to discover new channels
        System.out.println("🔍 Attempting broader search discovery for: " + q);
        try {
            var discoveredChannels = connector.search(q, Math.max(size, 10));
            System.out.println("📡 API search returned " + discoveredChannels.size() + " channels");

            // Save discovered channels to database
            int saved = 0;
            for (var channel : discoveredChannels) {
                try {
                    var existing = channelRepo.findByPlatformAndPlatformId(
                            channel.getPlatform(),
                            channel.getPlatformId()
                    );
                    if (existing.isEmpty()) {
                        channelService.upsertChannel(channel);
                        saved++;
                    }
                } catch (Exception e) {
                    System.out.println("❌ Error saving discovered channel: " + e.getMessage());
                }
            }

            System.out.println("💾 Saved " + saved + " new channels to database");

            // Now search the database again for the updated results
            if (saved > 0) {
                try {
                    Page<ChannelWithStatsDTO> updatedResults = channelRepo.search(q, PageRequest.of(page, size));
                    if (!updatedResults.isEmpty()) {
                        model.addAttribute("page", updatedResults);
                        model.addAttribute("results", updatedResults);
                        model.addAttribute("info", "Found " + saved + " new channels and added them to the database!");
                        System.out.println("✅ Returning updated database results after discovery");
                        return "leaderboard";
                    }
                } catch (Exception e) {
                    System.out.println("❌ Updated database search failed: " + e.getMessage());
                }
            }

            // If we discovered channels but they don't match the search, show a message
            if (!discoveredChannels.isEmpty()) {
                model.addAttribute("info", "Discovered " + discoveredChannels.size() + " channels but none match your search exactly. Try browsing the leaderboard or search again.");
            }

        } catch (Exception e) {
            System.out.println("❌ Discovery search failed: " + e.getMessage());
            e.printStackTrace();
            model.addAttribute("error", "Search failed: " + e.getMessage());
        }

        // FOURTH: If still no results, trigger background discovery for future searches
        System.out.println("🔄 Triggering background discovery for: " + q);
        try {
            smartDiscoveryService.opportunisticDiscovery(q);
            System.out.println("📈 Background discovery triggered");
        } catch (Exception e) {
            System.out.println("❌ Background discovery failed: " + e.getMessage());
        }

        // Return empty results with helpful message
        model.addAttribute("page", Page.empty());
        model.addAttribute("results", Page.empty());
        model.addAttribute("info", "No results found. We've started discovering channels matching '" + q + "' - try searching again in a few moments, or check if your search term is correct.");

        return "leaderboard";
    }
}