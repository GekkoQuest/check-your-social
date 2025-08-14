package quest.gekko.cys.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import quest.gekko.cys.domain.Channel;
import quest.gekko.cys.repo.ChannelRepo;
import quest.gekko.cys.service.connector.PlatformConnector;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

@Service
@RequiredArgsConstructor
public class IngestionScheduler {
    private final List<PlatformConnector> connectors;
    private final ChannelRepo channelRepo;
    private final StatsService statsService;
    private final RankingService rankingService;

    // 02:10 UTC daily
    @Scheduled(cron = "0 10 2 * * *", zone = "UTC")
    @Transactional
    public void runDailySnapshot() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        for (PlatformConnector pc : connectors) {
            var channels = channelRepo.findAll().stream()
                    .filter(c -> c.getPlatform()==pc.platform()).toList();
            for (Channel c : channels) {
                var counters = pc.fetchCounters(c.getPlatformId());
                statsService.snapshot(c, counters, today);
                // TODO: add small backoff to respect quotas
            }
            rankingService.computeDailyRanks(today, pc.platform());
        }
    }
}