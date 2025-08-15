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

        // Check if it's a specific handle that should resolve to one channel
        if (q.startsWith("@") || q.startsWith("http")) {
            System.out.println("🎯 Attempting to resolve specific channel: " + q);
            try {
                Optional<Channel> one = connector.resolveAndHydrate(q);
                if (one.isPresent()) {
                    Channel found = channelService.upsertChannel(one.get());
                    System.out.println("✅ Channel found, redirecting to: /channel/" + found.getId());
                    return "redirect:/channel/" + found.getId();
                } else {
                    System.out.println("❌ Channel not found for: " + q);
                    model.addAttribute("results", Page.empty());
                    model.addAttribute("page", Page.empty());
                    model.addAttribute("error", "Channel not found: " + q);
                    return "leaderboard";
                }
            } catch (Exception e) {
                System.out.println("❌ Error resolving channel: " + e.getMessage());
                e.printStackTrace();
                model.addAttribute("error", "Error resolving channel: " + e.getMessage());
                model.addAttribute("page", Page.empty());
                model.addAttribute("results", Page.empty());
                return "leaderboard";
            }
        }

        // For general search terms, use the enhanced search method with stats
        System.out.println("🔍 Searching existing database with stats for: " + q);
        try {
            // Use the enhanced search method that includes stats
            Page<ChannelWithStatsDTO> results = channelRepo.search(q, PageRequest.of(page, size));

            System.out.println("📚 Found " + results.getContent().size() + " channels with stats");

            if (!results.isEmpty()) {
                model.addAttribute("page", results);
                model.addAttribute("results", results);
                System.out.println("✅ Returning database results with stats");
                return "leaderboard";
            }

            // If no database results, trigger discovery and show empty results
            System.out.println("🌐 No database results, triggering discovery for: " + q);
            try {
                smartDiscoveryService.opportunisticDiscovery(q);
                System.out.println("📈 Discovery triggered for future searches");
            } catch (Exception e) {
                System.out.println("❌ Discovery failed: " + e.getMessage());
            }

            // Return empty results with a helpful message
            model.addAttribute("page", Page.empty());
            model.addAttribute("results", Page.empty());
            model.addAttribute("info", "No results found. Discovery has been triggered - try searching again in a few moments.");

        } catch (Exception e) {
            System.out.println("❌ Search failed: " + e.getMessage());
            e.printStackTrace();
            model.addAttribute("error", "Search failed: " + e.getMessage());
            model.addAttribute("page", Page.empty());
            model.addAttribute("results", Page.empty());
        }

        return "leaderboard";
    }
}