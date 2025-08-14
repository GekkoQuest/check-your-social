package quest.gekko.cys.dto;

import quest.gekko.cys.domain.Platform;

public interface ChannelWithLatestStat {
    Long getId();

    Platform getPlatform();

    String getHandle();
    String getTitle();
    String getAvatarUrl();

    Long getSubscribers();
    Long getFollowers();
    Long getViews();
    Long getVideos();
}