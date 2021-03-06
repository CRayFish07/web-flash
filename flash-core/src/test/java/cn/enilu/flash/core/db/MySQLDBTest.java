package cn.enilu.flash.core.db;

import com.google.common.base.Joiner;
import org.apache.commons.dbcp2.BasicDataSource;
import org.joda.time.DateTimeUtils;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.persistence.Table;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class MySQLDBTest extends DBTestBase {

    Logger logger = LoggerFactory.getLogger(MySQLDBTest.class);
	private static String url = "jdbc:mysql://localhost:3306/flash_test?useUnicode=true&characterEncoding=UTF-8";
	private static Context context;

	@BeforeClass
	public static void setUp() throws Exception {
		BasicDataSource dataSource = new BasicDataSource();
		dataSource.setDriverClassName("com.mysql.jdbc.Driver");
		dataSource.setUsername("root");
		dataSource.setPassword("root");
		dataSource.setUrl(url);
		DB db = new DB(dataSource, DB.Type.MySQL);

		context = new Context();
		context.db = db;
		context.dataSource = dataSource;

		executeSql(dataSource, "drop table if exists sample");
		executeSql(dataSource,
				"create table sample(id bigint auto_increment primary key, a varchar(200)," +
                        " phone1 varchar(30), phone_2 varchar(30)," +
                        " money decimal(14,2), created_at datetime NOT NULL, updated_at datetime NOT NULL)");
	}

	@Test
	public void testLock() {
		prepareData(2);
		Sample sample = db.lock(Sample.class, 1);
		assertNotNull(sample);

		sample = db.lock(sample);
		assertNotNull(sample);

		String sql = db.from(Sample.class).where("id", 1).lock().toSQL();
		assertEquals("select * from sample where id = ? for update", sql);
	}

	@Test
	public void testReplace() {
		Sample sample = new Sample("a");
		sample.setId(1L);
		db.insert(sample);
		sample.setA("b");
		int n = db.replace(sample);
		assertEquals(1, n);
	}

	@Test
	public void testBatchInsert() {
		List<Sample> samples = new ArrayList<>();
		for (int i = 0; i < 20; i++) {
			Sample sample = new Sample(String.valueOf(i));
			samples.add(sample);
		}

		int n = db.batchInsert(samples);
		assertEquals(samples.size(), n);
	}

    @Test
    public void testExcuteSql() {
        String sql = "insert into sample(a, phone1, phone_2, created_at, updated_at) value(?, ? , ?, ?, now())";
        int n = db.execute(sql, "fangdtest", "1283434", "124455", new java.sql.Date(DateTimeUtils.currentTimeMillis()));
        assertEquals(1, n);
    }

    @Test
    public void batchUpdate()
    {
        List<Sample> samples = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            Sample sample = new Sample(String.valueOf(i));
            samples.add(sample);
        }
        int n = db.batchInsert(samples);

        StringBuilder condition = new StringBuilder();
        //condition="id in (?, ?, ?)"
        condition.append("id in (");
        Object[] ids = new Object[20];
        String[] specialChars = new String[20];
        for(int i = 0; i < 20; i++)
        {
            specialChars[i] = "?";
            ids[i] = new Long(i+1);
        }
        //condition.append(Joiner.on(",").join(specialChars)).append(")");
        Joiner.on(",").appendTo(condition, specialChars).append(")");
        logger.info("condition: {}", condition.toString());
        db.update(Sample.class, "a", "batchupdate", condition.toString(), ids);

        for (Sample sample : db.all(Sample.class)) {
            assertEquals("batchupdate", sample.getA());
        }
    }

	@Test
	public void testBatchReplace() {
		List<Sample> samples = new ArrayList<>();
		for (int i = 0; i < 20; i++) {
			Sample sample = new Sample(String.valueOf(i));
			sample.setId(i + 1L);
			samples.add(sample);
		}

		int n = db.batchInsert(samples);
		assertEquals(samples.size(), n);

		for (Sample sample : samples) {
			sample.setA("-1");
		}
		db.batchReplace(samples);
		for (Sample sample : db.all(Sample.class)) {
			assertEquals("-1", sample.getA());
		}
	}

    @Test
    public void testSelectValue()
    {
        List<Sample> samples = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            Sample sample = new Sample(String.valueOf(i));
            samples.add(sample);
        }
        int n = db.batchInsert(samples);

        String countSql = "select count(id) from Sample where id < ?";
        int total = db.selectValue(Integer.class, countSql, 10L);
        logger.info("total is {}", total);

        assertEquals(9, total);
    }

/*    @Test
    public void testSelectValues()
    {
        List<Sample> samples = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            Sample sample = new Sample(String.valueOf(i));
            samples.add(sample);
        }
        int n = db.batchInsert(samples);

        String countSql = "select id, sum(id) total from Sample where id < ? group by id";
        List<IdTotal> idTotals = db.selectValues(IdTotal.class, countSql, 10L);
        logger.info("total is {}", idTotals.size());

        assertEquals(9, idTotals.size());
    }*/

    @Table
    public class IdTotal
    {
        private Long id;

        public Long getTotal() {
            return total;
        }

        public void setTotal(Long total) {
            this.total = total;
        }

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        private Long total;


    }

	@AfterClass
	public static void tearDown() throws Exception {
		context.dataSource.close();
	}

	protected Context getContext() {
		return context;
	}

	@After
	public void clean() {
		getContext().db.execute("delete from sample");
		getContext().db.execute("alter table sample auto_increment=1");
	}

}
