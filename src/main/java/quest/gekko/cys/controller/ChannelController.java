package quest.gekko.cys.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import quest.gekko.cys.domain.Channel;
import quest.gekko.cys.domain.Platform;
import quest.gekko.cys.repo.DailyStatRepo;
import quest.gekko.cys.service.ChannelService;
import quest.gekko.cys.service.connector.PlatformConnector;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class ChannelController {
    private final ChannelService channelService;
    private final DailyStatRepo statRepo;
    private final List<PlatformConnector> connectors;

    // Fixed URL mapping to match what templates generate
    @GetMapping("/channel/{id}")
    public String view(@PathVariable Long id, Model m) {
        var channel = channelService.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        var history = statRepo.findByChannelIdOrderBySnapshotDateAsc(channel.getId());
        m.addAttribute("channel", channel);
        m.addAttribute("history", history);
        return "channel";
    }

    // Keep the old endpoint for backward compatibility
    @GetMapping("/channels/{platform}/{handle}")
    public String viewByHandle(@PathVariable Platform platform, @PathVariable String handle, Model m) {
        var channel = channelService.findByHandle(platform, handle)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return "redirect:/channel/" + channel.getId();
    }


}