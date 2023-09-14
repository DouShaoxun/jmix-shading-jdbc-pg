package cn.cruder.dousx.jsjpg.alg;

import cn.hutool.core.date.DatePattern;
import cn.hutool.core.date.DateUtil;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.shardingsphere.sharding.api.sharding.standard.PreciseShardingValue;
import org.apache.shardingsphere.sharding.api.sharding.standard.RangeShardingValue;
import org.apache.shardingsphere.sharding.api.sharding.standard.StandardShardingAlgorithm;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * 按月分表的 Sharding 算法
 */
@Getter
@Slf4j
public class HisDataMonthShardingAlgorithm implements StandardShardingAlgorithm<Date> {

    private final ThreadLocal<SimpleDateFormat> dateFormatThreadLocal = ThreadLocal.withInitial(() -> new SimpleDateFormat("yyyy_MM"));

    private Properties props;

    private final Date tableLowerDate = DateUtil.parseDate("2023-01-01").toJdkDate();


    /**
     * 精确路由算法
     *
     * @param availableTargetNames 可用的表列表（配置文件中配置的 actual-data-nodes会被解析成 列表被传递过来）
     * @param shardingValue        精确的值
     * @return 结果表
     */
    @Override
    public String doSharding(Collection<String> availableTargetNames, PreciseShardingValue<Date> shardingValue) {
        Date value = shardingValue.getValue();
        // 根据精确值获取路由表
        String actuallyTableName = shardingValue.getLogicTableName() + shardingSuffix(value);
        if (availableTargetNames.contains(actuallyTableName)) {
            return actuallyTableName;
        }
        return null;
    }

    /**
     * 范围路由算法
     *
     * @param availableTargetNames 可用的表列表（配置文件中配置的 actual-data-nodes会被解析成 列表被传递过来）
     * @param shardingValue        值范围
     * @return 路由后的结果表
     */
    @Override
    public Collection<String> doSharding(Collection<String> availableTargetNames, RangeShardingValue<Date> shardingValue) {
        Date rangeLowerDate;
        if (shardingValue.getValueRange().hasLowerBound()) {
            rangeLowerDate = shardingValue.getValueRange().lowerEndpoint();
        } else {
            rangeLowerDate = tableLowerDate;
        }

        Date rangeUpperDate;
        if (shardingValue.getValueRange().hasUpperBound()) {
            rangeUpperDate = shardingValue.getValueRange().upperEndpoint();
        } else {
            rangeUpperDate = DateUtil.offsetMonth(new Date(), 1);
        }
        rangeUpperDate = DateUtil.endOfMonth(rangeUpperDate);
        if (log.isDebugEnabled()) {
            log.debug("范围 {} {} ",
                    DateUtil.format(rangeLowerDate, DatePattern.NORM_DATETIME_PATTERN),
                    DateUtil.format(rangeUpperDate, DatePattern.NORM_DATETIME_PATTERN));
        }
        List<String> tableNames = new ArrayList<>();
        while (rangeLowerDate.before(rangeUpperDate)) {
            String actuallyTableName = shardingValue.getLogicTableName() + shardingSuffix(rangeLowerDate);
            if (availableTargetNames.contains(actuallyTableName)) {
                tableNames.add(actuallyTableName);
            }
            rangeLowerDate = DateUtil.offsetMonth(rangeLowerDate, 1);
        }
        if (log.isDebugEnabled()) {
            log.debug("tables: {}", tableNames);
        }
        return tableNames;
    }

    /**
     * sharding 表后缀_yyyy_MM
     */
    private String shardingSuffix(Date shardingValue) {
        return "_" + dateFormatThreadLocal.get().format(shardingValue);
    }


    @Override
    public String getType() {
        return "HIS_DATA_SPI_BASED";
    }


    @Override
    public void init(Properties properties) {
        this.props = properties;
    }
}

