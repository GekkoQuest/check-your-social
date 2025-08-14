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
    @Value("${sa.youtube.apiKey:}") String apiKey;

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

        // prefer the `customUrl` if present (e.g., "@mkbhd")
        Object customUrl = snip.get("customUrl");
        c.setHandle(customUrl != null ? customUrl.toString() : ("@" + c.getTitle().toLowerCase().replaceAll("\\s+","")));

        Map<String, Map<String,String>> thumbs = (Map<String, Map<String,String>>) snip.get("thumbnails");
        if (thumbs != null && thumbs.get("default") != null) c.setAvatarUrl(thumbs.get("default").get("url"));
        c.setCountry((String) snip.getOrDefault("country", null));
        return c;
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

        // You can either: (A) hydrate each by id (extra API call per item), or
        // (B) map from the search snippet (fast). We'll do (B) for speed.
        return items.stream()
                .map(it -> {
                    Map<String,Object> idObj = (Map<String,Object>) it.get("id");
                    Map<String,Object> snip  = (Map<String,Object>) it.get("snippet");
                    if (idObj == null || snip == null) return null;

                    String channelId = (String) idObj.get("channelId");
                    if (channelId == null || channelId.isBlank()) return null;

                    Channel c = new Channel();
                    c.setPlatform(Platform.YOUTUBE);
                    c.setPlatformId(channelId);
                    c.setTitle((String) snip.getOrDefault("channelTitle", ""));
                    // search results don’t include customUrl; keep handle null here (it’s fine for the list)
                    Map<String, Map<String,String>> thumbs = (Map<String, Map<String,String>>) snip.get("thumbnails");
                    if (thumbs != null && thumbs.get("default") != null) c.setAvatarUrl(thumbs.get("default").get("url"));
                    return c;
                })
                .filter(c -> c != null)
                .toList();
    }
}