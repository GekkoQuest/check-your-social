package quest.gekko.cys.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import quest.gekko.cys.domain.Channel;
import quest.gekko.cys.domain.DailyStat;
import quest.gekko.cys.repo.DailyStatRepo;

import java.time.LocalDate;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class StatsService {
    private final DailyStatRepo statRepo;

    @Transactional
    public DailyStat snapshot(final Channel channel, final Map<String, Long> counters, final LocalDate date) {
        return statRepo.findTopByChannelIdOrderBySnapshotDateDesc(channel.getId())
                .filter(last -> last.getSnapshotDate().equals(date))
                .orElseGet(() -> {
                    DailyStat dailyStat = new DailyStat();
                    dailyStat.setChannel(channel);
                    dailyStat.setSnapshotDate(date);
                    dailyStat.setSubscribers(counters.getOrDefault("subscribers",0L));
                    dailyStat.setViews(counters.getOrDefault("views",0L));
                    dailyStat.setVideos(counters.getOrDefault("videos",0L));
                    dailyStat.setFollowers(counters.getOrDefault("followers",0L));
                    dailyStat.setLiveViews(counters.getOrDefault("liveViews",0L));
                    return statRepo.save(dailyStat);
                });
    }
}