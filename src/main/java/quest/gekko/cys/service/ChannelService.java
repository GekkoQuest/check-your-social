package quest.gekko.cys.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import quest.gekko.cys.domain.Channel;
import quest.gekko.cys.domain.DailyStat;
import quest.gekko.cys.domain.Platform;
import quest.gekko.cys.repo.ChannelRepo;
import quest.gekko.cys.repo.DailyStatRepo;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ChannelService {
    private final ChannelRepo channelRepo;
    private final DailyStatRepo statRepo;

    @Transactional
    public Channel upsertChannel(final Channel channel) {
        return channelRepo.findByPlatformAndPlatformId(channel.getPlatform(), channel.getPlatformId())
                .map(ex -> {
                    ex.setTitle(channel.getTitle());
                    ex.setHandle(channel.getHandle());
                    ex.setAvatarUrl(channel.getAvatarUrl());
                    ex.setCountry(channel.getCountry());
                    return channelRepo.save(ex);
                })
                .orElseGet(() -> channelRepo.save(channel));
    }

    @Transactional
    public Channel upsertChannelIdentityOnly(final Channel channel) {
        return channelRepo.findByPlatformAndPlatformId(channel.getPlatform(), channel.getPlatformId())
                .orElseGet(() -> {
                    // Only save if it doesn't exist, with minimal data
                    Channel minimal = new Channel();
                    minimal.setPlatform(channel.getPlatform());
                    minimal.setPlatformId(channel.getPlatformId());
                    minimal.setTitle(channel.getTitle() != null ? channel.getTitle() : "Unknown");
                    minimal.setHandle(channel.getHandle() != null ? channel.getHandle() : "@unknown");
                    minimal.setAvatarUrl(channel.getAvatarUrl());
                    return channelRepo.save(minimal);
                });
    }

    // Add missing findById method
    public Optional<Channel> findById(Long id) {
        return channelRepo.findById(id);
    }

    public Optional<Channel> findByHandle(Platform platform, String handle) {
        return channelRepo.findByPlatformAndHandleIgnoreCase(platform, handle);
    }

    public List<DailyStat> history(Long channelId) {
        return statRepo.findByChannelIdOrderBySnapshotDateAsc(channelId);
    }
}