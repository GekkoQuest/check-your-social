package quest.gekko.cys.service.core;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import quest.gekko.cys.domain.Channel;
import quest.gekko.cys.domain.Platform;
import quest.gekko.cys.domain.RankSnapshot;
import quest.gekko.cys.repository.DailyStatRepository;
import quest.gekko.cys.repository.RankSnapshotRepository;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RankingService {
    private final DailyStatRepository statRepo;
    private final RankSnapshotRepository rankRepo;

    @Transactional
    public void computeDailyRanks(LocalDate date, Platform platform) {
        record Pair(Long channelId, long val) {}

        List<Pair> vals = statRepo.findAllOn(date).stream()
                .filter(ds -> ds.getChannel().getPlatform()==platform)
                .map(ds -> new Pair(ds.getChannel().getId(),
                        platform==Platform.YOUTUBE ? (ds.getSubscribers()==null?0:ds.getSubscribers())
                                : (ds.getFollowers()==null?0:ds.getFollowers())))
                .sorted((a,b)->Long.compare(b.val, a.val))
                .toList();

        int rank = 1;
        for (Pair pr : vals) {
            RankSnapshot rs = new RankSnapshot();
            rs.setSnapshotDate(date);
            rs.setPlatform(platform);
            rs.setMetric(platform==Platform.YOUTUBE?"subscribers":"followers");
            Channel c = new Channel(); c.setId(pr.channelId());
            rs.setChannel(c);
            rs.setRank(rank++);
            rankRepo.save(rs);
        }
    }
}