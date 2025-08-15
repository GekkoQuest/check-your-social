package quest.gekko.cys.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import quest.gekko.cys.domain.Channel;
import quest.gekko.cys.domain.Platform;
import quest.gekko.cys.web.dto.ChannelWithStatsDTO;

import java.util.List;
import java.util.Optional;

public interface ChannelRepository extends JpaRepository<Channel, Long> {
    Optional<Channel> findByPlatformAndPlatformId(final Platform platform, final String platformId);
    Optional<Channel> findByPlatformAndHandleIgnoreCase(final Platform platform, final String handle);

    // Enhanced search method returning Object arrays for manual DTO mapping
    @Query(value = """
        SELECT 
               c.id, 
               c.platform, 
               c.handle, 
               c.title, 
               c.avatar_url,
               COALESCE(latest.subscribers, 0), 
               COALESCE(latest.followers, 0), 
               COALESCE(latest.views, 0), 
               COALESCE(latest.videos, 0)
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
        WHERE (lower(c.title) like lower(concat('%', :q, '%')) 
               OR lower(c.handle) like lower(concat('%', :q, '%'))
               OR lower(c.platform_id) like lower(concat('%', :q, '%')))
        ORDER BY 
          case when lower(c.handle) = lower(:q) then 1
               when lower(c.title) = lower(:q) then 2
               when lower(c.handle) like lower(concat(:q, '%')) then 3
               when lower(c.title) like lower(concat(:q, '%')) then 4
               else 5 end,
          COALESCE(latest.subscribers, latest.followers, 0) DESC
        """, nativeQuery = true)
    List<Object[]> searchRaw(@Param("q") final String q);

    // Wrapper method to convert Object[] to DTO with pagination
    default Page<ChannelWithStatsDTO> search(String query, Pageable pageable) {
        List<Object[]> results = searchRaw(query);
        List<ChannelWithStatsDTO> dtos = results.stream()
                .map(ChannelWithStatsDTO::new)
                .toList();

        // Manual pagination
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), dtos.size());
        List<ChannelWithStatsDTO> pageContent = start < dtos.size() ?
                dtos.subList(start, end) : List.of();

        return new PageImpl<>(pageContent, pageable, dtos.size());
    }

    // Specific search for exact handle matches
    @Query("""
        select c from Channel c 
        where lower(c.handle) = lower(:handle)
           or lower(c.handle) = lower(concat('@', :handle))
           or lower(c.title) = lower(:handle)
        """)
    Optional<Channel> findByExactMatch(@Param("handle") final String handle);

    // Leaderboard query returning Object arrays for manual DTO mapping
    @Query(value = """
      SELECT 
             c.id, 
             c.platform, 
             c.handle, 
             c.title, 
             c.avatar_url,
             COALESCE(latest.subscribers, 0), 
             COALESCE(latest.followers, 0), 
             COALESCE(latest.views, 0), 
             COALESCE(latest.videos, 0)
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
      """, nativeQuery = true)
    List<Object[]> leaderboardRaw(@Param("platform") final String platform);

    // Wrapper method to convert Object[] to DTO with pagination
    default Page<ChannelWithStatsDTO> leaderboard(String platform, Pageable pageable) {
        List<Object[]> results = leaderboardRaw(platform);
        List<ChannelWithStatsDTO> dtos = results.stream()
                .map(ChannelWithStatsDTO::new)
                .toList();

        // Manual pagination
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), dtos.size());
        List<ChannelWithStatsDTO> pageContent = start < dtos.size() ?
                dtos.subList(start, end) : List.of();

        return new PageImpl<>(pageContent, pageable, dtos.size());
    }
}