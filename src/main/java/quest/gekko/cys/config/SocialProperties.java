package quest.gekko.cys.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for social media integrations
 */
@Configuration
@EnableConfigurationProperties({
        SocialProperties.YouTube.class,
        SocialProperties.Twitch.class,
        SocialProperties.Security.class
})
public class SocialProperties {

    @ConfigurationProperties("social.youtube")
    public record YouTube(String apiKey) {}

    @ConfigurationProperties("social.twitch")
    public record Twitch(String clientId, String clientSecret) {}

    @ConfigurationProperties("security.admin")
    public record Security(String username, String password) {}
}