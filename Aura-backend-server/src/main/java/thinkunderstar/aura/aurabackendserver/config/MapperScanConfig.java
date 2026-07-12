package thinkunderstar.aura.aurabackendserver.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("thinkunderstar.aura.aurabackendserver.mapper")
public class MapperScanConfig {
}
