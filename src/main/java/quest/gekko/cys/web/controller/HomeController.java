package quest.gekko.cys.web.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import quest.gekko.cys.domain.Platform;
import quest.gekko.cys.web.dto.ChannelWithLatestStat;
import quest.gekko.cys.repository.ChannelRepository;
import quest.gekko.cys.repository.DailyStatRepository;

import java.time.LocalDate;
import java.util.List;

@Controller
@RequiredArgsConstructor
public class HomeController {

    private final ChannelRepository channelRepository;
    private final DailyStatRepository statRepo;

    @GetMapping("/")
    public String home(Model model) {
        // Add default platform for the search form
        model.addAttribute("platform", Platform.YOUTUBE);

        // Get statistics for the homepage
        try {
            // Total channels
            long totalChannels = channelRepository.count();

            // YouTube vs Twitch breakdown
            long youtubeChannels = channelRepository.findAll().stream()
                    .filter(c -> c.getPlatform() == Platform.YOUTUBE)
                    .count();
            long twitchChannels = totalChannels - youtubeChannels;

            // Recent activity (stats from last 7 days)
            long recentStats = statRepo.findAll().stream()
                    .filter(s -> s.getSnapshotDate().isAfter(LocalDate.now().minusDays(7)))
                    .count();

            LocalDate cutoffDate = LocalDate.now().minusDays(7);

            // Top YouTube channels (limit 8 for homepage)
            var topYouTubeChannels = channelRepository.leaderboard("YOUTUBE", cutoffDate, PageRequest.of(0, 8));

            // Top Twitch channels (limit 8 for homepage)
            var topTwitchChannels = channelRepository.leaderboard("TWITCH", cutoffDate, PageRequest.of(0, 8));

            // Add to model
            model.addAttribute("totalChannels", totalChannels);
            model.addAttribute("youtubeChannels", youtubeChannels);
            model.addAttribute("twitchChannels", twitchChannels);
            model.addAttribute("recentStats", recentStats);
            model.addAttribute("topYouTubeChannels", topYouTubeChannels.getContent());
            model.addAttribute("topTwitchChannels", topTwitchChannels.getContent());

            // Show rapid mode indicator
            model.addAttribute("isRapidMode", totalChannels < 1000);

        } catch (Exception e) {
            // Fallback if stats fail
            model.addAttribute("totalChannels", 0L);
            model.addAttribute("youtubeChannels", 0L);
            model.addAttribute("twitchChannels", 0L);
            model.addAttribute("recentStats", 0L);
            model.addAttribute("topYouTubeChannels", List.<ChannelWithLatestStat>of());
            model.addAttribute("topTwitchChannels", List.<ChannelWithLatestStat>of());
            model.addAttribute("isRapidMode", true);
        }

        return "index";
    }
}