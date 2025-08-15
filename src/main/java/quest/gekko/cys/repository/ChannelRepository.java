package quest.gekko.cys.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import quest.gekko.cys.domain.Channel;
import quest.gekko.cys.domain.Platform;
import quest.gekko.cys.web.dto.ChannelWithLatestStat;

import java.util.Optional;

public interface ChannelRepository extends JpaRepository<Channel, Long> {
    Optional<Channel> findByPlatformAndPlatformId(final Platform platform, final String platformId);
    Optional<Channel> findByPlatformAndHandleIgnoreCase(final Platform platform, final String handle);

    // Enhanced search method
    @Query("""
        select c from Channel c 
        where lower(c.title) like lower(concat('%', :q, '%')) 
           or lower(c.handle) like lower(concat('%', :q, '%'))
           or lower(c.platformId) like lower(concat('%', :q, '%'))
        order by 
          case when lower(c.handle) = lower(:q) then 1
               when lower(c.title) = lower(:q) then 2
               when lower(c.handle) like lower(concat(:q, '%')) then 3
               when lower(c.title) like lower(concat(:q, '%')) then 4
               else 5 end,
          c.title
        """)
    Page<Channel> search(@Param("q") final String q, final Pageable pageable);

    // Specific search for exact handle matches
    @Query("""
        select c from Channel c 
        where lower(c.handle) = lower(:handle)
           or lower(c.handle) = lower(concat('@', :handle))
           or lower(c.title) = lower(:handle)
        """)
    Optional<Channel> findByExactMatch(@Param("handle") final String handle);

    // Leaderboard query with actual stats
    @Query(value = """
      SELECT 
             c.id as id, 
             c.platform as platform, 
             c.handle as handle, 
             c.title as title, 
             c.avatar_url as avatarUrl,
             COALESCE(latest.subscribers, 0) as subscribers, 
             COALESCE(latest.followers, 0) as followers, 
             COALESCE(latest.views, 0) as views, 
             COALESCE(latest.videos, 0) as videos
      FROM channel c
      LEFT JOIN (
          SELECT DISTINCT ON (ds.channel_id) 
                 ds.channel_id,
                 ds.subscribers,
                 ds.followers,
                 ds.views,
                 ds.videos
          FROM daily_stat ds
          ORDER BY ds.channel_id, ds.snapshot_date DESC
      ) latest ON latest.channel_id = c.id
      WHERE c.platform = :platform
      ORDER BY COALESCE(latest.subscribers, latest.followers, 0) DESC NULLS LAST
      """,
            countQuery = "SELECT count(c.id) FROM channel c WHERE c.platform = :platform",
            nativeQuery = true)
    Page<ChannelWithLatestStat> leaderboard(@Param("platform") final String platform, final Pageable pageable);
}