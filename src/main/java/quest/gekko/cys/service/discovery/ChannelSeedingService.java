package quest.gekko.cys.service.discovery;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import quest.gekko.cys.domain.Platform;
import quest.gekko.cys.service.integration.connector.PlatformConnector;
import quest.gekko.cys.service.core.ChannelService;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ChannelSeedingService {

    private final Map<Platform, PlatformConnector> connectorsByPlatform;
    private final ChannelService channelService;

    // Popular tech channels to seed the database
    private final List<String> POPULAR_TECH_CHANNELS = List.of(
            "@mkbhd", "@veritasium", "@3blue1brown", "@techlinked",
            "@unboxtherapy", "@austin", "@ijustine", "@dave2d",
            "@mkiceandfire", "@computerphile", "@numberphile",
            "@engineerguy", "@smarter", "@tomscott"
    );

    private final List<String> POPULAR_GAMING_CHANNELS = List.of(
            "@pewdiepie", "@mrbeast", "@dude", "@jacksepticeye",
            "@markiplier", "@gametheory", "@matpat", "@ninja"
    );

    private final List<String> POPULAR_EDUCATION_CHANNELS = List.of(
            "@khanacademy", "@crashcourse", "@tedx", "@bigthink",
            "@minutephysics", "@vsauce", "@scishow", "@veritasium"
    );

    /**
     * Seed the database with popular channels
     */
    public void seedPopularChannels() {
        var youtubeConnector = connectorsByPlatform.get(Platform.YOUTUBE);
        if (youtubeConnector == null) return;

        List<String> allChannels = List.of(
                POPULAR_TECH_CHANNELS,
                POPULAR_GAMING_CHANNELS,
                POPULAR_EDUCATION_CHANNELS
        ).stream().flatMap(List::stream).toList();

        for (String handle : allChannels) {
            try {
                var channelOpt = youtubeConnector.resolveAndHydrate(handle);
                if (channelOpt.isPresent()) {
                    channelService.upsertChannel(channelOpt.get());
                    System.out.println("✓ Added: " + handle);
                } else {
                    System.out.println("✗ Not found: " + handle);
                }

                // Be nice to the API
                Thread.sleep(200);

            } catch (Exception e) {
                System.err.println("Error adding " + handle + ": " + e.getMessage());
            }
        }
    }

    /**
     * Search-based discovery of channels in specific niches
     */
    public void discoverChannelsInNiche(String searchTerm, int maxResults) {
        var youtubeConnector = connectorsByPlatform.get(Platform.YOUTUBE);
        if (youtubeConnector == null) return;

        try {
            var channels = youtubeConnector.search(searchTerm, maxResults);
            for (var channel : channels) {
                channelService.upsertChannelIdentityOnly(channel);
                System.out.println("Discovered: " + channel.getTitle());
            }
        } catch (Exception e) {
            System.err.println("Error discovering channels for '" + searchTerm + "': " + e.getMessage());
        }
    }
}