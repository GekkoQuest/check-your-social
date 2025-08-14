package quest.gekko.cys.dto;

import quest.gekko.cys.domain.Platform;

/**
 * Unified Channel DTO replacing ChannelDto and ChannelOverviewDTO
 */
public record ChannelDTO(
        Long id,
        Platform platform,
        String platformId,
        String title,
        String handle,
        String avatarUrl,
        String country,

        // Stats - nullable for cases where stats aren't needed
        Long subscribers,
        Long followers,
        Long views,
        Long videos,
        Long liveViews
) {

    /**
     * Create a minimal ChannelDTO without stats
     */
    public static ChannelDTO minimal(Long id, Platform platform, String title, String handle, String avatarUrl) {
        return new ChannelDTO(id, platform, null, title, handle, avatarUrl, null, null, null, null, null, null);
    }

    /**
     * Create a full ChannelDTO with stats
     */
    public static ChannelDTO withStats(Long id, Platform platform, String platformId, String title,
                                       String handle, String avatarUrl, String country,
                                       Long subscribers, Long followers, Long views, Long videos, Long liveViews) {
        return new ChannelDTO(id, platform, platformId, title, handle, avatarUrl, country,
                subscribers, followers, views, videos, liveViews);
    }

    /**
     * Get the primary metric based on platform
     */
    public Long getPrimaryMetric() {
        return platform == Platform.YOUTUBE ? subscribers : followers;
    }

    /**
     * Get the primary metric name based on platform
     */
    public String getPrimaryMetricName() {
        return platform == Platform.YOUTUBE ? "subscribers" : "followers";
    }
}