package quest.gekko.cys.web.dto;

import quest.gekko.cys.domain.Platform;

public interface ChannelWithLatestStat {
    Long id();

    Platform platform();

    String handle();
    String title();
    String avatarUrl();

    Long subscribers();
    Long followers();
    Long views();
    Long videos();
}