package quest.gekko.cys.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import quest.gekko.cys.domain.Channel;
import quest.gekko.cys.domain.Platform;
import quest.gekko.cys.web.dto.ChannelWithLatestStat;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ChannelRepository extends JpaRepository<Channel, Long> {

    Optional<Channel> findByPlatformAndPlatformId(Platform platform, String platformId);

    Optional<Channel> findByPlatformAndHandleIgnoreCase(Platform platform, String handle);

    @Query("SELECT c FROM Channel c WHERE lower(c.title) LIKE lower(concat('%', :q, '%')) OR lower(c.handle) LIKE lower(concat('%', :q, '%'))")
    Page<Channel> search(@Param("q") String q, Pageable pageable);

    // Optimized leaderboard query with better performance
    @Query(value = """
        WITH latest_stats AS (
            SELECT DISTINCT ON (ds.channel_id) 
                   ds.channel_id,
                   ds.subscribers,
                   ds.followers,
                   ds.views,
                   ds.videos,
                   ds.snapshot_date
            FROM daily_stat ds
            WHERE ds.snapshot_date >= :cutoffDate
            ORDER BY ds.channel_id, ds.snapshot_date DESC
        )
        SELECT 
               c.id as id, 
               c.platform as platform, 
               c.handle as handle, 
               c.title as title, 
               c.avatar_url as avatarUrl,
               COALESCE(ls.subscribers, 0) as subscribers, 
               COALESCE(ls.followers, 0) as followers, 
               COALESCE(ls.views, 0) as views, 
               COALESCE(ls.videos, 0) as videos
        FROM channel c
        LEFT JOIN latest_stats ls ON ls.channel_id = c.id
        WHERE c.platform = :platform
        ORDER BY COALESCE(ls.subscribers, ls.followers, 0) DESC NULLS LAST
        """,
            countQuery = "SELECT count(c.id) FROM channel c WHERE c.platform = :platform",
            nativeQuery = true)
    Page<ChannelWithLatestStat> leaderboard(
            @Param("platform") String platform,
            @Param("cutoffDate") LocalDate cutoffDate,
            Pageable pageable
    );

    // Find channels without recent stats for batch processing
    @Query(value = """
        SELECT c.* FROM channel c
        LEFT JOIN (
            SELECT DISTINCT ON (channel_id) channel_id, snapshot_date
            FROM daily_stat
            ORDER BY channel_id, snapshot_date DESC
        ) latest ON latest.channel_id = c.id
        WHERE latest.snapshot_date IS NULL 
           OR latest.snapshot_date < :cutoffDate
        ORDER BY c.created_at ASC
        LIMIT :limit
        """, nativeQuery = true)
    List<Channel> findChannelsNeedingSnapshot(@Param("cutoffDate") LocalDate cutoffDate, @Param("limit") int limit);

    // Count channels by platform
    long countByPlatform(Platform platform);

    // Find potential duplicates
    @Query("""
        SELECT c FROM Channel c 
        WHERE c.platform = :platform 
        AND lower(c.handle) = lower(:handle)
        AND c.id != :excludeId
        """)
    List<Channel> findPotentialDuplicates(@Param("platform") Platform platform,
                                          @Param("handle") String handle,
                                          @Param("excludeId") Long excludeId);
}