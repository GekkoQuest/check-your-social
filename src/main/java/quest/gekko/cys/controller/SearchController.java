package quest.gekko.cys.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import quest.gekko.cys.domain.Channel;
import quest.gekko.cys.domain.Platform;
import quest.gekko.cys.repo.DailyStatRepo;
import quest.gekko.cys.service.ChannelService;
import quest.gekko.cys.service.SmartDiscoveryService;
import quest.gekko.cys.service.connector.PlatformConnector;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Controller
@RequiredArgsConstructor
public class SearchController {

    private final Map<Platform, PlatformConnector> connectorsByPlatform;
    private final ChannelService channelService;
    private final DailyStatRepo statRepo;
    private final SmartDiscoveryService smartDiscoveryService;

    @GetMapping("/search")
    public String search(
            @RequestParam String q,
            @RequestParam Platform platform,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            Model model
    ) {
        PlatformConnector connector = connectorsByPlatform.get(platform);
        model.addAttribute("q", q);
        model.addAttribute("platform", platform);

        // If it's a handle or a full URL, resolve ONE channel (detail-accurate)
        if (q.startsWith("@") || q.startsWith("http")) {
            Optional<Channel> one = connector.resolveAndHydrate(q);
            if (one.isPresent()) {
                Channel found = channelService.upsertChannel(one.get());
                // Redirect to the channel page instead of showing search results
                return "redirect:/channel/" + found.getId();
            } else {
                model.addAttribute("results", List.of());
                return "leaderboard";
            }
        }

        // Otherwise, broad search returns a LIST
        List<Channel> results = connector.search(q, size);

        // If we didn't find enough results, trigger opportunistic discovery
        if (results.size() < 3) {
            smartDiscoveryService.opportunisticDiscovery(q);
            // Search again after discovery
            results = connector.search(q, size);
        }

        // Upsert minimal identities and populate counters for display
        results = results.stream().map(channel -> {
            Channel saved = channelService.upsertChannelIdentityOnly(channel);

            // Try to get latest stats for display
            var latestStat = statRepo.findTopByChannelIdOrderBySnapshotDateDesc(saved.getId());
            if (latestStat.isPresent()) {
                var stat = latestStat.get();
                saved.getCounters().put("subscribers", stat.getSubscribers() != null ? stat.getSubscribers() : 0L);
                saved.getCounters().put("followers", stat.getFollowers() != null ? stat.getFollowers() : 0L);
                saved.getCounters().put("views", stat.getViews() != null ? stat.getViews() : 0L);
                saved.getCounters().put("videos", stat.getVideos() != null ? stat.getVideos() : 0L);
            }

            return saved;
        }).toList();

        model.addAttribute("results", results);
        return "leaderboard";
    }
}