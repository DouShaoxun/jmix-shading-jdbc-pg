package cn.cruder.dousx.jsjpg;

import cn.cruder.dousx.jsjpg.entity.ReportInfoEntity;
import cn.cruder.dousx.jsjpg.entity.User;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DatePattern;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.extra.spring.SpringUtil;
import io.jmix.core.Metadata;
import io.jmix.core.Sort;
import io.jmix.core.UnconstrainedDataManager;
import io.jmix.core.querycondition.LogicalCondition;
import io.jmix.core.querycondition.PropertyCondition;
import io.jmix.data.PersistenceHints;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Random;

@Slf4j
@SpringBootTest
class JmixShadingJdbcPgApplicationTests {

	@Autowired
	UnconstrainedDataManager unconstrainedDataManager;
	@Autowired
	Metadata metadata;
	@PersistenceContext
	protected EntityManager em;

	TransactionTemplate transaction;

	@Autowired
	protected void setTransactionManager(PlatformTransactionManager transactionManager) {
		transaction = new TransactionTemplate(transactionManager);
		transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
	}

	@Test
	void insertTests() {
		Random random = new Random();
		UnconstrainedDataManager bean = SpringUtil.getBean(UnconstrainedDataManager.class);
		LogicalCondition condition = LogicalCondition.and();
		condition.add(PropertyCondition.equal("username", "admin"));
		User user = unconstrainedDataManager.load(User.class).condition(condition).one();
		if (user == null) {
			throw new IllegalArgumentException("user is null!!!!");
		}
		for (int i = 0; i < 1000; i++) {
			ReportInfoEntity entity = bean.create(ReportInfoEntity.class);
			entity.setName(IdUtil.getSnowflakeNextIdStr());
			// 2023-2024
			int year = 2023 + random.nextInt(2);
			// 月份 0~11
			int month = random.nextInt(12);
			// 1-25号  避免大小月
			int day = 1 + random.nextInt(25);
			Calendar calendar = Calendar.getInstance();
			calendar.set(year, month, day);
			entity.setUploadTime(calendar.getTime());
			entity.setUser(user);
			bean.save(entity);
		}
		log.info("插入数据结束...");
	}

	@Test
	void selectTest() {
		LogicalCondition condition = LogicalCondition.and();
		condition.add(PropertyCondition.greater("uploadTime", DateUtil.parse("2023-09-01").toJdkDate()));
		condition.add(PropertyCondition.less("uploadTime", DateUtil.parse("2024-01-06").toJdkDate()));
		List<ReportInfoEntity> list = unconstrainedDataManager.load(ReportInfoEntity.class)
				.condition(condition)
				.hint(PersistenceHints.SOFT_DELETION, true)
				.list();
		log.info("{}", CollUtil.size(list));
	}

	private void selectOrder(Date startDate, Date endDate) {
		LogicalCondition condition = LogicalCondition.and();
		condition.add(PropertyCondition.greater("uploadTime", startDate));
		condition.add(PropertyCondition.less("uploadTime", endDate));
		List<ReportInfoEntity> list = unconstrainedDataManager.load(ReportInfoEntity.class)
				.condition(condition)
				.hint(PersistenceHints.SOFT_DELETION, true)
				.sort(Sort.by(List.of(
						Sort.Order.desc("name"),
						Sort.Order.desc("uploadTime")
				)))
				.list();

		int size = CollUtil.size(list);
		log.info("{}", size);
		if (size > 0) {
			for (int i = 0; i < size; i++) {
				ReportInfoEntity entity = list.get(i);
				log.info("{} {}", entity.getName(),
						DateUtil.format(entity.getUploadTime(), DatePattern.ISO8601_PATTERN));
			}
		}
	}

	@Test
	void orderSuccessTest() {
		Date startDate = DateUtil.parse("2023-09-01").toJdkDate();
		Date endDate = DateUtil.parse("2023-09-30").toJdkDate();
		selectOrder(startDate, endDate);
	}

	@Test
	void orderFailTest() {
		LogicalCondition condition = LogicalCondition.and();
		Date start = DateUtil.parse("2023-09-01").toJdkDate();
		Date end = DateUtil.parse("2023-11-12").toJdkDate();
		selectOrder(start, end);
	}

	/**
	 * 查找并排序
	 */
	private void nativeSqlQueryOrder(String startTime, String endTime) {
		Date start = DateUtil.parse(startTime).toJdkDate();
		Date end = DateUtil.parse(endTime).toJdkDate();
		Query nativeQuery = em.createNativeQuery("SELECT * FROM JSJPG_REPORT_INFO_ENTITY  WHERE  upload_time BETWEEN ?1 AND ?2  ORDER BY upload_time", ReportInfoEntity.class);
		nativeQuery.setParameter(1, start);
		nativeQuery.setParameter(2, end);
		List resultList = nativeQuery.getResultList();
		log.info("{}", CollUtil.size(resultList));
	}

	/**
	 * 不排序
	 */
	private void nativeSqlQuery(String startTime, String endTime) {
		Date start = DateUtil.parse(startTime).toJdkDate();
		Date end = DateUtil.parse(endTime).toJdkDate();
		Query nativeQuery = em.createNativeQuery("SELECT * FROM JSJPG_REPORT_INFO_ENTITY  WHERE  upload_time BETWEEN ?1 AND ?2 ", ReportInfoEntity.class);
		nativeQuery.setParameter(1, start);
		nativeQuery.setParameter(2, end);
		List resultList = nativeQuery.getResultList();
		log.info("{}", CollUtil.size(resultList));
	}
	@Test
	void nativeSqlQuerySuccess() {
		log.debug("查询 但不排序");
		nativeSqlQuery("2023-06-01","2024-07-20");
	}

	@Test
	void nativeSqlQueryOrderSuccess() {
		log.debug("查询 且排序");
		nativeSqlQueryOrder("2023-06-01","2023-06-30");
	}

	@Test
	void nativeSqlQueryOrderFail() {
		log.debug("查询 且排序");
		nativeSqlQueryOrder("2023-06-01","2024-11-12");
	}


}
