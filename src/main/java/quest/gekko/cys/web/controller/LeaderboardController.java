package quest.gekko.cys.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import quest.gekko.cys.domain.Platform;
import quest.gekko.cys.repository.ChannelRepository;
import quest.gekko.cys.web.dto.ChannelWithLatestStat;

@Controller
@RequiredArgsConstructor
public class LeaderboardController {
    private final ChannelRepository channelRepo;

    @GetMapping("/leaderboard")
    public String leaderboard(@RequestParam(defaultValue = "YOUTUBE") Platform platform,
                              @RequestParam(defaultValue = "0") int page,
                              @RequestParam(defaultValue = "20") int size,
                              @RequestParam(required = false) String q,
                              Model m) {
        try {
            Page<ChannelWithLatestStat> p;

            // If there's a search query, handle it as search results
            if (q != null && !q.trim().isEmpty()) {
                // For search queries, use the search method instead of leaderboard
                p = channelRepo.search(q.trim(), PageRequest.of(page, size))
                        .map(channel -> new ChannelWithLatestStatImpl(
                                channel.getId(),
                                channel.getPlatform(),
                                channel.getHandle(),
                                channel.getTitle(),
                                channel.getAvatarUrl(),
                                channel.getCounters().getOrDefault("subscribers", 0L),
                                channel.getCounters().getOrDefault("followers", 0L),
                                channel.getCounters().getOrDefault("views", 0L),
                                channel.getCounters().getOrDefault("videos", 0L)
                        ));

                m.addAttribute("q", q);
                m.addAttribute("isSearch", true);
            } else {
                // Regular leaderboard
                p = channelRepo.leaderboard(platform.name(), PageRequest.of(page, size));
                m.addAttribute("isSearch", false);
            }

            // Debug: log the results
            System.out.println("Leaderboard query returned " + p.getContent().size() + " items");
            for (var item : p.getContent()) {
                System.out.println("- " + item.id() + ": " + item.title() + " (" + item.handle() + ")");
            }

            m.addAttribute("platform", platform);
            m.addAttribute("page", p);
            m.addAttribute("results", p); // Add this for template compatibility
            return "leaderboard";
        } catch (Exception e) {
            // Fallback if leaderboard query fails
            System.err.println("Leaderboard query failed: " + e.getMessage());
            e.printStackTrace();
            m.addAttribute("platform", platform);
            m.addAttribute("results", Page.empty());
            m.addAttribute("page", Page.empty());
            m.addAttribute("error", "Unable to load leaderboard: " + e.getMessage());
            m.addAttribute("q", q);
            m.addAttribute("isSearch", q != null && !q.trim().isEmpty());
            return "leaderboard";
        }
    }

    // Implementation class for search results
    private static class ChannelWithLatestStatImpl implements ChannelWithLatestStat {
        private final Long id;
        private final Platform platform;
        private final String handle;
        private final String title;
        private final String avatarUrl;
        private final Long subscribers;
        private final Long followers;
        private final Long views;
        private final Long videos;

        public ChannelWithLatestStatImpl(Long id, Platform platform, String handle, String title,
                                         String avatarUrl, Long subscribers, Long followers, Long views, Long videos) {
            this.id = id;
            this.platform = platform;
            this.handle = handle;
            this.title = title;
            this.avatarUrl = avatarUrl;
            this.subscribers = subscribers;
            this.followers = followers;
            this.views = views;
            this.videos = videos;
        }

        @Override public Long id() { return id; }
        @Override public Platform platform() { return platform; }
        @Override public String handle() { return handle; }
        @Override public String title() { return title; }
        @Override public String avatarUrl() { return avatarUrl; }
        @Override public Long subscribers() { return subscribers; }
        @Override public Long followers() { return followers; }
        @Override public Long views() { return views; }
        @Override public Long videos() { return videos; }
    }
}