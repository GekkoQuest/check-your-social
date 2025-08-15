package quest.gekko.cys.service.integration.connector;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import quest.gekko.cys.domain.Channel;
import quest.gekko.cys.domain.Platform;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class YouTubeConnector implements PlatformConnector {
    private final WebClient http;

    @Value("${social.youtube.api-key:}")
    String apiKey;

    private static final Pattern HANDLE_IN_DESC = Pattern.compile("@[A-Za-z0-9._]{3,30}");
    private static final Pattern URL_HANDLE = Pattern.compile("/@([A-Za-z0-9._]{3,30})(?:[/?#].*)?$");

    @Override
    public Platform platform() { return Platform.YOUTUBE; }

    @Override
    public Optional<Channel> resolveAndHydrate(String handleOrUrl) {
        if (apiKey == null || apiKey.isBlank()) return Optional.empty();
        if (handleOrUrl == null || handleOrUrl.isBlank()) return Optional.empty();

        // 1) If input is a handle, use forHandle (KEEP '@')
        if (handleOrUrl.startsWith("@")) {
            return fetchByHandle(handleOrUrl.trim());
        }

        // 2) If it's a URL, try to extract channelId or handle or vanity parts
        if (handleOrUrl.startsWith("http")) {
            Optional<String> byId = extractChannelIdFromUrl(handleOrUrl);
            if (byId.isPresent()) return hydrateById(byId.get());

            Optional<String> byHandle = extractHandleFromUrl(handleOrUrl);
            if (byHandle.isPresent()) return fetchByHandle(byHandle.get());

            Optional<String> vanity = extractVanityFromUserOrC(handleOrUrl);
            if (vanity.isPresent()) {
                // Try as a handle first (legacy vanity names often equal handle)
                Optional<Channel> ch = fetchByHandle("@" + vanity.get());
                if (ch.isPresent()) return ch;
                // Fallback: search by vanity text
                return searchAndHydrate(vanity.get(), 1).stream().findFirst();
            }
            // last resort: treat the whole URL as a search query
        }

        // 3) Otherwise, search by text and hydrate first result
        return searchAndHydrate(handleOrUrl, 1).stream().findFirst();
    }

    private Optional<Channel> fetchByHandle(String handleWithAt) {
        Map<?, ?> chan = http.get()
                .uri(uri -> uri.scheme("https").host("www.googleapis.com").path("/youtube/v3/channels")
                        .queryParam("part", "snippet,statistics")
                        .queryParam("forHandle", handleWithAt) // must include '@'
                        .queryParam("key", apiKey)
                        .build())
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        List<Map<String, Object>> items = safeItems(chan);
        if (items.isEmpty()) return Optional.empty();
        return Optional.of(mapChannel(items.getFirst()));
    }

    private Optional<Channel> hydrateById(String channelId) {
        if (channelId == null || channelId.isBlank()) return Optional.empty();

        Map<?, ?> chan = http.get()
                .uri(uri -> uri.scheme("https").host("www.googleapis.com").path("/youtube/v3/channels")
                        .queryParam("part", "snippet,statistics")
                        .queryParam("id", channelId)
                        .queryParam("key", apiKey)
                        .build())
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        List<Map<String, Object>> items = safeItems(chan);
        if (items.isEmpty()) return Optional.empty();
        return Optional.of(mapChannel(items.get(0)));
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Long> fetchCounters(String platformId) {
        if (apiKey == null || apiKey.isBlank()) return Map.of();
        if (platformId == null || platformId.isBlank()) return Map.of();

        Map<?, ?> resp = http.get()
                .uri(uri -> uri.scheme("https").host("www.googleapis.com").path("/youtube/v3/channels")
                        .queryParam("part", "statistics")
                        .queryParam("id", platformId)
                        .queryParam("key", apiKey)
                        .build())
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        if (resp == null) return Map.of();
        List<Map<String, Object>> items = safeItems(resp);
        if (items.isEmpty()) return Map.of();

        Map<String, Object> stats = (Map<String, Object>) items.get(0).get("statistics");
        if (stats == null) return Map.of();

        long subs = parseLong(stats.get("subscriberCount"));
        long views = parseLong(stats.get("viewCount"));
        long videos = parseLong(stats.get("videoCount"));
        return Map.of("subscribers", subs, "views", views, "videos", videos);
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Channel> search(String query, int maxResults) {
        return searchAndHydrate(query, maxResults);
    }

    // ---- Helpers ----

    @SuppressWarnings("unchecked")
    private List<Channel> searchAndHydrate(String query, int maxResults) {
        if (apiKey == null || apiKey.isBlank()) return List.of();
        if (query == null || query.isBlank()) return List.of();

        int capped = Math.min(Math.max(maxResults, 1), 25);

        Map<?, ?> search = http.get()
                .uri(uri -> uri.scheme("https").host("www.googleapis.com").path("/youtube/v3/search")
                        .queryParam("part", "snippet")
                        .queryParam("type", "channel")
                        .queryParam("q", query)
                        .queryParam("maxResults", String.valueOf(capped))
                        .queryParam("key", apiKey)
                        .build())
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        List<Map<String, Object>> items = (List<Map<String, Object>>) (search != null ? search.get("items") : List.of());
        if (items == null || items.isEmpty()) return List.of();

        // Extract channel IDs
        List<String> channelIds = items.stream()
                .map(it -> (Map<String, Object>) it.get("id"))
                .filter(Objects::nonNull)
                .map(idObj -> (String) idObj.get("channelId"))
                .filter(id -> id != null && !id.isBlank())
                .toList();

        if (channelIds.isEmpty()) return List.of();

        // Batch fetch details (up to 50)
        String idParam = String.join(",", channelIds);
        Map<?, ?> channelsResponse = http.get()
                .uri(uri -> uri.scheme("https").host("www.googleapis.com").path("/youtube/v3/channels")
                        .queryParam("part", "snippet,statistics")
                        .queryParam("id", idParam)
                        .queryParam("key", apiKey)
                        .build())
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        List<Map<String, Object>> channelItems =
                (List<Map<String, Object>>) (channelsResponse != null ? channelsResponse.get("items") : List.of());
        if (channelItems == null || channelItems.isEmpty()) return List.of();

        return channelItems.stream()
                .map(this::mapChannel)
                .filter(ch -> ch != null && ch.getTitle() != null && !ch.getTitle().isBlank())
                .toList();
    }

    @SuppressWarnings("unchecked")
    private Channel mapChannel(Map<String, Object> item) {
        if (item == null) return null;

        Map<String, Object> snip = (Map<String, Object>) item.get("snippet");
        if (snip == null) return null;

        // id can be String (channels.list) or Map (search.list)
        String id = null;
        Object idObj = item.get("id");
        if (idObj instanceof String) {
            id = (String) idObj;
        } else if (idObj instanceof Map) {
            id = (String) ((Map<String, Object>) idObj).get("channelId");
        }

        Channel c = new Channel();
        c.setPlatform(Platform.YOUTUBE);
        if (id != null) c.setPlatformId(id);
        c.setTitle((String) snip.getOrDefault("title", ""));

        // Handle
        c.setHandle(determineChannelHandle(snip, id));

        // Avatar: prefer high -> medium -> default
        Map<String, Map<String, String>> thumbs =
                (Map<String, Map<String, String>>) snip.get("thumbnails");
        if (thumbs != null) {
            Map<String, String> best = thumbs.get("high");
            if (best == null) best = thumbs.get("medium");
            if (best == null) best = thumbs.get("default");
            if (best != null) c.setAvatarUrl(best.get("url"));
        }

        c.setCountry((String) snip.getOrDefault("country", null));
        return c;
    }

    private String determineChannelHandle(Map<String, Object> snippet, String channelId) {
        // 1) Prefer snippet.customUrl (can be "@name", "name", or a URL)
        Object raw = snippet != null ? snippet.get("customUrl") : null;
        if (raw != null) {
            String cu = raw.toString().trim();
            String handle = normalizeCustomUrlToHandle(cu);
            if (handle != null) return handle;
        }

        // 2) Look for @handle inside description
        String description = snippet != null ? (String) snippet.get("description") : null;
        if (description != null) {
            Matcher m = HANDLE_IN_DESC.matcher(description);
            if (m.find()) return m.group();
        }

        // 3) Last-resort: derive from channelId (UC…)
        if (channelId != null && channelId.length() >= 10 && channelId.startsWith("UC")) {
            return "@" + channelId.substring(2, 10).toLowerCase();
        }

        // 4) Fallback to unknown
        return "@unknown" + (System.currentTimeMillis() % 10000);
    }

    private String normalizeCustomUrlToHandle(String cu) {
        if (cu == null || cu.isBlank()) return null;

        String s = cu.trim();
        if (s.startsWith("@")) {
            return s.matches("@[A-Za-z0-9._]{3,30}") ? s : null;
        }
        if (s.startsWith("http")) {
            Matcher m = URL_HANDLE.matcher(s);
            if (m.find()) {
                String candidate = "@" + m.group(1);
                return candidate.matches("@[A-Za-z0-9._]{3,30}") ? candidate : null;
            }
            // Sometimes customUrl is "https://youtube.com/c/Name" (no @) -> try to extract tail
            String tail = s.replaceFirst(".*/(c|user)/", "");
            if (!tail.equals(s)) {
                tail = tail.replaceAll("[/?#].*$", "");
                if (!tail.isBlank()) {
                    String candidate = "@" + tail;
                    if (candidate.matches("@[A-Za-z0-9._]{3,30}")) return candidate;
                }
            }
            return null;
        }
        // Plain value like "mkbhd"
        String candidate = s.startsWith("@") ? s : "@" + s;
        return candidate.matches("@[A-Za-z0-9._]{3,30}") ? candidate : null;
    }

    private Optional<String> extractChannelIdFromUrl(String url) {
        int i = url.indexOf("/channel/");
        if (i != -1) {
            String id = url.substring(i + "/channel/".length()).split("[/?#]")[0];
            if (!id.isBlank()) return Optional.of(id);
        }
        return Optional.empty();
    }

    private Optional<String> extractHandleFromUrl(String url) {
        Matcher m = URL_HANDLE.matcher(url);
        if (m.find()) return Optional.of("@" + m.group(1));
        return Optional.empty();
    }

    private Optional<String> extractVanityFromUserOrC(String url) {
        int i = url.indexOf("/user/");
        int baseLen;
        if (i != -1) {
            baseLen = "/user/".length();
        } else {
            i = url.indexOf("/c/");
            if (i == -1) return Optional.empty();
            baseLen = "/c/".length();
        }
        String vanity = url.substring(i + baseLen).split("[/?#]")[0];
        return vanity.isBlank() ? Optional.empty() : Optional.of(vanity);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> safeItems(Map<?, ?> obj) {
        if (obj == null) return List.of();
        Object itemsObj = obj.get("items");
        if (!(itemsObj instanceof List<?> list)) return List.of();
        return (List<Map<String, Object>>) (List<?>) list;
    }

    private static long parseLong(Object o) {
        if (o == null) return 0L;
        if (o instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(o.toString());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
