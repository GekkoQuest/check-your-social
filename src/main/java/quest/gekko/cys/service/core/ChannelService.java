package quest.gekko.cys.service.core;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import quest.gekko.cys.domain.Channel;
import quest.gekko.cys.domain.DailyStat;
import quest.gekko.cys.domain.Platform;
import quest.gekko.cys.dto.ChannelDTO;
import quest.gekko.cys.repository.ChannelRepository;
import quest.gekko.cys.repository.DailyStatRepository;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ChannelService {

    private final ChannelRepository channelRepository;
    private final DailyStatRepository statRepository;

    @Cacheable(value = "channels", key = "#id")
    public Optional<Channel> findById(Long id) {
        return channelRepository.findById(id);
    }

    @Cacheable(value = "channels", key = "#platform + ':' + #handle")
    public Optional<Channel> findByHandle(Platform platform, String handle) {
        return channelRepository.findByPlatformAndHandleIgnoreCase(platform, handle);
    }

    public Page<Channel> search(String query, Pageable pageable) {
        return channelRepository.search(query, pageable);
    }

    @Cacheable(value = "channelHistory", key = "#channelId")
    public List<DailyStat> getHistory(Long channelId) {
        return statRepository.findByChannelIdOrderBySnapshotDateAsc(channelId);
    }

    @Transactional
    public Channel upsertChannel(Channel channel) {
        return channelRepository.findByPlatformAndPlatformId(channel.getPlatform(), channel.getPlatformId())
                .map(existing -> updateExistingChannel(existing, channel))
                .orElseGet(() -> channelRepository.save(channel));
    }

    @Transactional
    public Channel upsertChannelIdentityOnly(Channel channel) {
        return channelRepository.findByPlatformAndPlatformId(channel.getPlatform(), channel.getPlatformId())
                .orElseGet(() -> {
                    Channel minimal = createMinimalChannel(channel);
                    return channelRepository.save(minimal);
                });
    }

    /**
     * Convert to DTO with latest stats
     */
    public ChannelDTO toDTO(Channel channel) {
        var latestStat = statRepository.findTopByChannelIdOrderBySnapshotDateDesc(channel.getId());

        if (latestStat.isPresent()) {
            var stat = latestStat.get();
            return ChannelDTO.withStats(
                    channel.getId(), channel.getPlatform(), channel.getPlatformId(),
                    channel.getTitle(), channel.getHandle(), channel.getAvatarUrl(), channel.getCountry(),
                    stat.getSubscribers(), stat.getFollowers(), stat.getViews(),
                    stat.getVideos(), stat.getLiveViews()
            );
        } else {
            return ChannelDTO.minimal(
                    channel.getId(), channel.getPlatform(),
                    channel.getTitle(), channel.getHandle(), channel.getAvatarUrl()
            );
        }
    }

    private Channel updateExistingChannel(Channel existing, Channel updated) {
        // Only update non-null values
        if (updated.getTitle() != null) existing.setTitle(updated.getTitle());
        if (updated.getHandle() != null) existing.setHandle(updated.getHandle());
        if (updated.getAvatarUrl() != null) existing.setAvatarUrl(updated.getAvatarUrl());
        if (updated.getCountry() != null) existing.setCountry(updated.getCountry());

        return channelRepository.save(existing);
    }

    private Channel createMinimalChannel(Channel template) {
        Channel minimal = new Channel();
        minimal.setPlatform(template.getPlatform());
        minimal.setPlatformId(template.getPlatformId());
        minimal.setTitle(template.getTitle() != null ? template.getTitle() : "Unknown");
        minimal.setHandle(template.getHandle() != null ? template.getHandle() : "@unknown");
        minimal.setAvatarUrl(template.getAvatarUrl());
        minimal.setCountry(template.getCountry());
        return minimal;
    }
}