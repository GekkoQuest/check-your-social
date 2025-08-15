package quest.gekko.cys.service.integration.connector;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import quest.gekko.cys.domain.Channel;
import quest.gekko.cys.domain.Platform;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class YouTubeConnector implements PlatformConnector {
    private final WebClient http;
    @Value("${social.youtube.api-key:}") String apiKey;

    @Override public Platform platform() { return Platform.YOUTUBE; }

    @Override
    public Optional<Channel> resolveAndHydrate(String handleOrUrl) {
        if (apiKey == null || apiKey.isBlank()) return Optional.empty();

        // If it's a handle, use forHandle (best path for @mkbhd)
        if (handleOrUrl.startsWith("@")) {
            String handle = handleOrUrl.substring(1);
            Map<?,?> chan = http.get()
                    .uri(uri -> uri.scheme("https").host("www.googleapis.com").path("/youtube/v3/channels")
                            .queryParam("part","snippet,statistics")
                            .queryParam("forHandle", handle)
                            .queryParam("key", apiKey)
                            .build())
                    .retrieve().bodyToMono(Map.class).block();

            @SuppressWarnings("unchecked")
            List<Map<String,Object>> items = (List<Map<String,Object>>) chan.get("items");
            if (items == null || items.isEmpty()) return Optional.empty();

            return Optional.of(mapChannel(items.get(0)));
        }

        // If it's a full URL, try to extract channel ID or username quickly
        if (handleOrUrl.startsWith("http")) {
            // naive extract; you can improve this for /c/, /@/, /channel/ patterns
            String url = handleOrUrl;
            String id = null;
            // /channel/UC... => use id
            int i = url.indexOf("/channel/");
            if (i != -1) {
                id = url.substring(i + "/channel/".length()).split("[/?#]")[0];
            }
            if (id != null) {
                return hydrateById(id);
            }
            // fallback: if it contains /@handle, use that
            i = url.indexOf("/@");
            if (i != -1) {
                String handle = url.substring(i + 2).split("[/?#]")[0];
                return resolveAndHydrate("@" + handle);
            }
            // last resort: search API
        }

        // Otherwise, try search by name
        Map<?,?> search = http.get()
                .uri(uri -> uri.scheme("https").host("www.googleapis.com").path("/youtube/v3/search")
                        .queryParam("part","snippet").queryParam("type","channel")
                        .queryParam("q", handleOrUrl)
                        .queryParam("maxResults", "1")
                        .queryParam("key", apiKey).build())
                .retrieve().bodyToMono(Map.class).block();

        @SuppressWarnings("unchecked")
        List<Map<String,Object>> sitems = (List<Map<String,Object>>) (search != null ? search.get("items") : List.of());
        if (sitems == null || sitems.isEmpty()) return Optional.empty();

        @SuppressWarnings("unchecked")
        String channelId = (String)((Map<String,Object>)sitems.get(0).get("id")).get("channelId");
        return hydrateById(channelId);
    }

    private Optional<Channel> hydrateById(String channelId) {
        Map<?,?> chan = http.get()
                .uri(uri -> uri.scheme("https").host("www.googleapis.com").path("/youtube/v3/channels")
                        .queryParam("part","snippet,statistics")
                        .queryParam("id", channelId).queryParam("key", apiKey).build())
                .retrieve().bodyToMono(Map.class).block();

        @SuppressWarnings("unchecked")
        List<Map<String,Object>> chItems = (List<Map<String,Object>>) (chan != null ? chan.get("items") : List.of());
        if (chItems == null || chItems.isEmpty()) return Optional.empty();

        return Optional.of(mapChannel(chItems.get(0)));
    }

    @SuppressWarnings("unchecked")
    private Channel mapChannel(Map<String,Object> item) {
        Map<String,Object> snip = (Map<String,Object>) item.get("snippet");
        String id = (String) item.get("id");

        Channel c = new Channel();
        c.setPlatform(Platform.YOUTUBE);
        c.setPlatformId(id);
        c.setTitle((String) snip.get("title"));

        // Enhanced handle logic - try multiple approaches
        String handle = determineChannelHandle(snip, id);
        c.setHandle(handle);

        Map<String, Map<String,String>> thumbs = (Map<String, Map<String,String>>) snip.get("thumbnails");
        if (thumbs != null && thumbs.get("default") != null) c.setAvatarUrl(thumbs.get("default").get("url"));
        c.setCountry((String) snip.getOrDefault("country", null));
        return c;
    }

    /**
     * Determine the best handle for a channel using multiple strategies
     */
    private String determineChannelHandle(Map<String,Object> snippet, String channelId) {
        // Strategy 1: Use customUrl if available (best option)
        Object customUrl = snippet.get("customUrl");
        if (customUrl != null && !customUrl.toString().isBlank()) {
            String url = customUrl.toString();
            // Ensure it starts with @
            return url.startsWith("@") ? url : "@" + url;
        }

        // Strategy 2: Try to extract from channel description or about
        String description = (String) snippet.get("description");
        if (description != null) {
            // Look for @mentions in description
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("@([a-zA-Z0-9_]{1,20})");
            java.util.regex.Matcher matcher = pattern.matcher(description);
            if (matcher.find()) {
                return "@" + matcher.group(1);
            }
        }

        // Strategy 3: Create handle from channel title (clean version)
        String title = (String) snippet.get("title");
        if (title != null && !title.isBlank()) {
            // Clean the title and make it handle-friendly
            String cleanTitle = title
                    .replaceAll("[^a-zA-Z0-9\\s]", "") // Remove special chars except spaces
                    .trim()
                    .replaceAll("\\s+", "") // Remove all spaces
                    .toLowerCase();

            if (cleanTitle.length() > 3 && cleanTitle.length() <= 20) {
                return "@" + cleanTitle;
            } else if (cleanTitle.length() > 20) {
                return "@" + cleanTitle.substring(0, 20);
            }
        }

        // Strategy 4: Use channel ID as last resort (shortened)
        if (channelId != null && channelId.length() > 8) {
            return "@" + channelId.substring(2, 10).toLowerCase(); // Skip "UC" prefix, take 8 chars
        }

        // Final fallback
        return "@unknown" + System.currentTimeMillis() % 10000; // Add timestamp to avoid duplicates
    }

    @Override
    public Map<String, Long> fetchCounters(String platformId) {
        if (apiKey==null || apiKey.isBlank()) return Map.of();
        Map<?,?> resp = http.get()
                .uri(uri -> uri.scheme("https").host("www.googleapis.com").path("/youtube/v3/channels")
                        .queryParam("part","statistics")
                        .queryParam("id", platformId).queryParam("key", apiKey).build())
                .retrieve().bodyToMono(Map.class).block();

        List<Map<String,Object>> items = (List<Map<String,Object>>) resp.get("items");
        Map<String,Object> stats = (Map<String,Object>) items.get(0).get("statistics");
        long subs = Long.parseLong((String) stats.getOrDefault("subscriberCount","0"));
        long views = Long.parseLong((String) stats.getOrDefault("viewCount","0"));
        long videos = Long.parseLong((String) stats.getOrDefault("videoCount","0"));
        return Map.of("subscribers", subs, "views", views, "videos", videos);
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Channel> search(String query, int maxResults) {
        if (apiKey == null || apiKey.isBlank()) return List.of();

        Map<?,?> search = http.get()
                .uri(uri -> uri.scheme("https").host("www.googleapis.com").path("/youtube/v3/search")
                        .queryParam("part","snippet")
                        .queryParam("type","channel")
                        .queryParam("q", query)
                        .queryParam("maxResults", String.valueOf(Math.min(Math.max(maxResults, 1), 25)))
                        .queryParam("key", apiKey)
                        .build())
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        List<Map<String,Object>> items = (List<Map<String,Object>>) (search != null ? search.get("items") : List.of());
        if (items == null || items.isEmpty()) return List.of();

        // Extract channel IDs from search results
        List<String> channelIds = items.stream()
                .map(it -> {
                    Map<String,Object> idObj = (Map<String,Object>) it.get("id");
                    return idObj != null ? (String) idObj.get("channelId") : null;
                })
                .filter(id -> id != null && !id.isBlank())
                .toList();

        if (channelIds.isEmpty()) return List.of();

        // Batch fetch full channel details (up to 50 at once)
        String channelIdsParam = String.join(",", channelIds);

        Map<?,?> channelsResponse = http.get()
                .uri(uri -> uri.scheme("https").host("www.googleapis.com").path("/youtube/v3/channels")
                        .queryParam("part","snippet,statistics")
                        .queryParam("id", channelIdsParam)
                        .queryParam("key", apiKey)
                        .build())
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        List<Map<String,Object>> channelItems = (List<Map<String,Object>>)
                (channelsResponse != null ? channelsResponse.get("items") : List.of());

        if (channelItems == null || channelItems.isEmpty()) return List.of();

        // Map to full Channel objects with enhanced handle logic
        return channelItems.stream()
                .map(this::mapChannel)
                .filter(channel -> channel != null && !channel.getTitle().isBlank())
                .toList();
    }
}