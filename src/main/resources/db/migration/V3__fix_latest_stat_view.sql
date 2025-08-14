DROP VIEW IF EXISTS v_latest_stat;

CREATE VIEW v_latest_stat AS
SELECT DISTINCT ON (ds.channel_id)
        ds.channel_id,
        ds.snapshot_date,
        ds.subscribers,
        ds.views,
        ds.videos,
        ds.followers,
        ds.live_views
        FROM daily_stat ds
        ORDER BY ds.channel_id, ds.snapshot_date DESC;