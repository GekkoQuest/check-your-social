package quest.gekko.cys.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import quest.gekko.cys.domain.Platform;
import quest.gekko.cys.service.connector.PlatformConnector;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Configuration
public class ConnectorConfig {

    @Bean
    public Map<Platform, PlatformConnector> connectorsByPlatform(List<PlatformConnector> connectors) {
        return connectors.stream()
                .collect(Collectors.toMap(PlatformConnector::platform, Function.identity()));
    }
}