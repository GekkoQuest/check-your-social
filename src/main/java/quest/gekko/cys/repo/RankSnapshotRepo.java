package quest.gekko.cys.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import quest.gekko.cys.domain.Platform;
import quest.gekko.cys.domain.RankSnapshot;

import java.time.LocalDate;
import java.util.List;

public interface RankSnapshotRepo extends JpaRepository<RankSnapshot, Long> {
    List<RankSnapshot> findBySnapshotDateAndPlatformAndMetricOrderByRankAsc(final LocalDate date, final Platform platform, final String metric);
}