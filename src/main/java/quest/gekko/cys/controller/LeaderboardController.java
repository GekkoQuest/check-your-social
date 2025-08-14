package quest.gekko.cys.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import quest.gekko.cys.domain.Platform;
import quest.gekko.cys.dto.ChannelWithLatestStat;
import quest.gekko.cys.repo.ChannelRepo;

@Controller
@RequiredArgsConstructor
public class LeaderboardController {
    private final ChannelRepo channelRepo;

    @GetMapping("/leaderboard")
    public String leaderboard(@RequestParam(defaultValue = "YOUTUBE") Platform platform,
                              @RequestParam(defaultValue = "0") int page,
                              @RequestParam(defaultValue = "20") int size,
                              Model m) {
        try {
            Page<ChannelWithLatestStat> p = channelRepo.leaderboard(platform.name(), PageRequest.of(page, size));

            // Debug: log the results
            System.out.println("Leaderboard query returned " + p.getContent().size() + " items");
            for (var item : p.getContent()) {
                System.out.println("- " + item.getId() + ": " + item.getTitle() + " (" + item.getHandle() + ")");
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
            m.addAttribute("error", "Unable to load leaderboard: " + e.getMessage());
            return "leaderboard";
        }
    }
}