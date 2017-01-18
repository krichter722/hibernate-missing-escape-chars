package richtercloud.hibernate.missing.escape.chars;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import org.apache.commons.io.FileUtils;
import org.apache.derby.jdbc.EmbeddedDriver;
import org.hibernate.Session;
import org.hibernate.dialect.Dialect;
import org.hibernate.engine.jdbc.dialect.internal.StandardDialectResolver;
import org.hibernate.engine.jdbc.dialect.spi.DatabaseMetaDataDialectResolutionInfoAdapter;
import org.hibernate.engine.jdbc.dialect.spi.DialectResolutionInfo;
import org.hibernate.engine.jdbc.dialect.spi.DialectResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shows that
 * {@link Dialect#getCreateSequenceStrings(java.lang.String, int, int) } is
 * missing SQL quotes ({@code ``}) around the sequence name. 
 * @author richter
 */
public class NewClass {
    private final static Logger LOGGER = LoggerFactory.getLogger(NewClass.class);

    public static void main(String[] args) throws SQLException, IOException {
        Class<?> databaseDriver = EmbeddedDriver.class;
        File databaseDir = File.createTempFile(NewClass.class.getSimpleName(), null);
        FileUtils.deleteQuietly(databaseDir);
        LOGGER.info(String.format("using '%s' as database directory", databaseDir.getAbsolutePath()));
        String databaseURL = String.format("jdbc:derby:%s", databaseDir.getAbsolutePath());
        Connection connection = DriverManager.getConnection(String.format("%s;create=true", databaseURL));
        connection.close();

        EntityManagerFactory entityManagerFactory = null;
        EntityManager entityManager = null;
        try {
            Map<Object, Object> properties = new HashMap<>();
            properties.put("javax.persistence.jdbc.url", databaseURL);
            properties.put("javax.persistence.jdbc.driver", databaseDriver.getName());
            entityManagerFactory = Persistence.createEntityManagerFactory("richtercloud_hibernate-missing-escape-chars_jar_1.0-beta2PU",
                    properties);
            entityManager = entityManagerFactory.createEntityManager();
            for(String sequenceName : new String[] {"without_minus", "with-minus"}) {
                LOGGER.info(String.format("tryping sequence name '%s'", sequenceName));
                entityManager.getTransaction().begin();
                Session session = entityManager.unwrap(Session.class);
                    //EntityManager.unwrap(Connection.class) fails due to
                    //`Exception in thread "main" javax.persistence.PersistenceException: Hibernate cannot unwrap interface java.sql.Connection`
                    //which doesn't make any sense, asked
                    //http://stackoverflow.com/questions/41710164/jpa-2-1-portable-way-to-get-a-connection-instance-from-hibernate
                    //for input
                try {
                    session.doWork((Connection workConnection) -> {
                        DialectResolver dialectResolver = new StandardDialectResolver();
                        DialectResolutionInfo dialectResolutionInfo = new DatabaseMetaDataDialectResolutionInfoAdapter(workConnection.getMetaData());
                        Dialect dialect =  dialectResolver.resolveDialect(dialectResolutionInfo);
                        String[] sqls = dialect.getCreateSequenceStrings(sequenceName, 0, 1);
                        if (sqls == null) {
                            throw new RuntimeException();
                                //Dialect.getCreateSequenceStrings doesn't
                                //document whether it can return null or not
                        }
                        for(String sql : sqls) {
                            LOGGER.debug(String.format("executing statement '%s'", sql));
                            try (Statement statement = workConnection.createStatement()) {
                                statement.execute(sql);
                            }
                        }
                    });
                } finally {
                    entityManager.getTransaction().commit();
                }
            }
        }finally {
            if(entityManager != null) {
                entityManager.close();
            }
            if(entityManagerFactory != null) {
                entityManagerFactory.close();
            }
        }
    }
}
