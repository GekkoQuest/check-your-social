package quest.gekko.cys.dto;

import quest.gekko.cys.domain.Platform;

public record ChannelOverviewDTO(
        Long id,

        Platform platform,

        String handle,
        String title,
        String avatarUrl,
        Long subscribers,
        Long followers,
        Long views,
        Long videos
) {}