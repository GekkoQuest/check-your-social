package quest.gekko.cys.dto;

public record ChannelDto(
        Long id,

        String title,
        String handle,
        String avatarUrl,
        String platformId,

        Long subscribers,
        Long followers,
        Long views,
        Long videos
) {}