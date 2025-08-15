package quest.gekko.cys.web.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import quest.gekko.cys.domain.Platform;
import quest.gekko.cys.repository.ChannelRepository;
import quest.gekko.cys.web.dto.ChannelWithStatsDTO;

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
            Page<ChannelWithStatsDTO> p;

            // If there's a search query, use the search method
            if (q != null && !q.trim().isEmpty()) {
                p = channelRepo.search(q.trim(), PageRequest.of(page, size));
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
                System.out.println("- " + item.getId() + ": " + item.getTitle() + " (" + item.getHandle() + ") - Subs: " + item.getSubscribers());
            }

            m.addAttribute("platform", platform);
            m.addAttribute("page", p);
            m.addAttribute("results", p);
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
}