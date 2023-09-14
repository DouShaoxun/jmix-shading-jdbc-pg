package cn.cruder.dousx.jsjpg.config;

import io.jmix.autoconfigure.data.JmixLiquibaseCreator;
import io.jmix.core.Stores;
import io.jmix.data.impl.liquibase.LiquibaseChangeLogProcessor;
import liquibase.integration.spring.SpringLiquibase;
import lombok.extern.slf4j.Slf4j;
import org.apache.shardingsphere.driver.api.ShardingSphereDataSourceFactory;
import org.apache.shardingsphere.infra.config.algorithm.AlgorithmConfiguration;
import org.apache.shardingsphere.sharding.api.config.ShardingRuleConfiguration;
import org.apache.shardingsphere.sharding.api.config.rule.ShardingTableRuleConfiguration;
import org.apache.shardingsphere.sharding.api.config.strategy.sharding.StandardShardingStrategyConfiguration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.autoconfigure.liquibase.LiquibaseProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Properties;

@Slf4j
@Configuration
public class DataSourceConfig {

    @Bean
    @Primary
    @ConfigurationProperties("main.datasource")
    DataSourceProperties dataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @Primary
    @ConfigurationProperties("main.datasource.hikari")
    DataSource dataSource(DataSourceProperties dataSourceProperties) throws SQLException {
        //Jmix默认数据源
        DataSource defDataSource = dataSourceProperties.initializeDataSourceBuilder().build();
        String hisMonthShardingAlgName = "his_month_sharding";
        ShardingRuleConfiguration shardingRuleConfiguration = new ShardingRuleConfiguration();
        ShardingTableRuleConfiguration shardingTableRule =
                // 逻辑表名要和javax.persistence.Table.name一致
                new ShardingTableRuleConfiguration(
                        "JSJPG_REPORT_INFO_ENTITY",
                        "jsjpg.JSJPG_REPORT_INFO_ENTITY_202$->{3..4}_$->{['01','02','03','04','05','06','07','08','09','10','11','12']}");
        shardingTableRule.setTableShardingStrategy(new StandardShardingStrategyConfiguration(
                "upload_time", hisMonthShardingAlgName));
        shardingRuleConfiguration.setTables(List.of(shardingTableRule));
        // 5.1.0
        //shardingRuleConfiguration.setShardingAlgorithms(Map.of(hisMonthShardingAlgName,
        //        new ShardingSphereAlgorithmConfiguration("HIS_DATA_SPI_BASED", new Properties())));

        shardingRuleConfiguration.setShardingAlgorithms(Map.of(hisMonthShardingAlgName,
                new AlgorithmConfiguration("HIS_DATA_SPI_BASED", new Properties())));

        Properties props = new Properties(); // 构建属性配置
        props.put("sql-show", true);
        // ShardingSphereDataSource
        DataSource shardingSphereDataSource = ShardingSphereDataSourceFactory.createDataSource(
                Map.of("jsjpg", defDataSource),
                List.of(shardingRuleConfiguration),
                props);
        log.info("dataSource: {}", shardingSphereDataSource.getClass());
        return shardingSphereDataSource;
    }

    @Bean(name = "jmix_Liquibase")
    @Primary
    public SpringLiquibase liquibase(DataSourceProperties dataSourceProperties,
                                     LiquibaseChangeLogProcessor processor,
                                     @Qualifier("jmix_LiquibaseProperties") LiquibaseProperties properties) {
        // liquibase这里如果是ShardingSphereDataSource会报错
        // 相当于单独创建一个HikariDataSource数据源
        DataSource defDataSource = dataSourceProperties.initializeDataSourceBuilder().build();
        log.info("JmixLiquibase dataSource: {}", defDataSource.getClass());
        return JmixLiquibaseCreator.create(defDataSource, properties, processor, Stores.MAIN);
    }
}
