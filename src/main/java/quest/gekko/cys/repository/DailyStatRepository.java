package quest.gekko.cys.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import quest.gekko.cys.domain.DailyStat;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DailyStatRepository extends JpaRepository<DailyStat, Long> {
    List<DailyStat> findByChannelIdOrderBySnapshotDateAsc(Long channelId);
    Optional<DailyStat> findTopByChannelIdOrderBySnapshotDateDesc(Long channelId);

    @Query("select ds from DailyStat ds where ds.snapshotDate=:d")
    List<DailyStat> findAllOn(@Param("d") LocalDate date);
}