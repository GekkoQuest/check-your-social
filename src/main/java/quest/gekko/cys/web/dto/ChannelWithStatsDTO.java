package quest.gekko.cys.web.dto;

import quest.gekko.cys.domain.Platform;

/**
 * Concrete DTO class to replace the problematic ChannelWithLatestStat projection interface
 */
public class ChannelWithStatsDTO {
    private final Long id;
    private final Platform platform;
    private final String handle;
    private final String title;
    private final String avatarUrl;
    private final Long subscribers;
    private final Long followers;
    private final Long views;
    private final Long videos;

    public ChannelWithStatsDTO(Long id, Platform platform, String handle, String title, String avatarUrl,
                               Long subscribers, Long followers, Long views, Long videos) {
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

    // Constructor for SQL result mapping
    public ChannelWithStatsDTO(Object[] row) {
        this.id = (Long) row[0];
        this.platform = Platform.valueOf((String) row[1]);
        this.handle = (String) row[2];
        this.title = (String) row[3];
        this.avatarUrl = (String) row[4];
        this.subscribers = row[5] != null ? ((Number) row[5]).longValue() : 0L;
        this.followers = row[6] != null ? ((Number) row[6]).longValue() : 0L;
        this.views = row[7] != null ? ((Number) row[7]).longValue() : 0L;
        this.videos = row[8] != null ? ((Number) row[8]).longValue() : 0L;
    }

    // Getters for Thymeleaf access
    public Long getId() { return id; }
    public Platform getPlatform() { return platform; }
    public String getHandle() { return handle; }
    public String getTitle() { return title; }
    public String getAvatarUrl() { return avatarUrl; }
    public Long getSubscribers() { return subscribers; }
    public Long getFollowers() { return followers; }
    public Long getViews() { return views; }
    public Long getVideos() { return videos; }

    // For interface compatibility (can be removed once all usage is updated)
    public Long id() { return id; }
    public Platform platform() { return platform; }
    public String handle() { return handle; }
    public String title() { return title; }
    public String avatarUrl() { return avatarUrl; }
    public Long subscribers() { return subscribers; }
    public Long followers() { return followers; }
    public Long views() { return views; }
    public Long videos() { return videos; }
}