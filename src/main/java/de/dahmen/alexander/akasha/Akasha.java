
package de.dahmen.alexander.akasha;

import de.dahmen.alexander.akasha.config.*;
import de.dahmen.alexander.akasha.config.lib.Config;
import de.dahmen.alexander.akasha.conversations.*;
import de.dahmen.alexander.akasha.core.conversation.Conversation;
import de.dahmen.alexander.akasha.core.conversation.DefaultConversationDispatch;
import de.dahmen.alexander.akasha.core.repository.JdaTaskRepository;
import de.dahmen.alexander.akasha.core.repository.JdaUserRepository;
import de.dahmen.alexander.akasha.repository.mysql.MysqlJdaTaskRepository;
import de.dahmen.alexander.akasha.repository.mysql.MysqlJdaUserRepository;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.security.auth.login.LoginException;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.core.AccountType;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.JDABuilder;
import net.dv8tion.jda.core.OnlineStatus;
import org.apache.commons.dbcp2.BasicDataSource;
import org.flywaydb.core.Flyway;
import de.dahmen.alexander.akasha.core.conversation.ConversationDispatch;
import de.dahmen.alexander.akasha.core.listener.AkashaListener;
import de.dahmen.alexander.akasha.core.listener.LifecycleListener;
import de.dahmen.alexander.akasha.core.service.CronService;
import de.dahmen.alexander.akasha.core.service.JdaTaskReminderService;
import de.dahmen.alexander.akasha.service.cronutil.CronUtilsCronService;
import de.dahmen.alexander.akasha.service.scheduled.ScheduledJdaTaskReminderService;

/**
 *
 * @author Alexander
 */
@Slf4j
public class Akasha {
    private static final String CONFIG_FILE = "config.properties"; // This file is in .gitignore
    private static final List<Class<?>> CONFIG_CLASSES = Collections.unmodifiableList(Arrays.asList(
            DatabaseConfig.class,
            DiscordConfig.class,
            ConversationConfig.class,
            ReminderConfig.class));
    
    private static JDA jda;
    private static Object[] closeOnShutdown;
    
    public static void main(String[] args) throws Exception {
        Config config = Config.load(CONFIG_FILE, CONFIG_CLASSES);
        DataSource database = createDataSource(config.get(DatabaseConfig.class));
        migrate(database);
        
        jda = createJDA(config.get(DiscordConfig.class).getToken());
        
        JdaTaskRepository taskRepository = new MysqlJdaTaskRepository(database);
        JdaUserRepository userRepository = new MysqlJdaUserRepository(jda, database);
        CronService cronService = new CronUtilsCronService();
        JdaTaskReminderService reminderService = new ScheduledJdaTaskReminderService(
                config.get(ReminderConfig.class),
                taskRepository, userRepository,
                cronService);
        
        reminderService.initialize();
        
        List<Conversation> conversations = Collections.unmodifiableList(Arrays.asList(
                new HelloConversation(),
                new MentionReplyConversation(),
                new CreateTaskConversation(
                        config.get(ConversationConfig.class),
                        reminderService,
                        cronService),
                new ShutdownConversation(),
                new DefaultFallbackConversation()));
        
        ConversationDispatch dispatch = new DefaultConversationDispatch(conversations);
        jda.addEventListener(new AkashaListener(dispatch));
        
        closeOnShutdown = new Object[] {
            database,
            reminderService,
        };
    }
    
    public static void shutdown() {
        for (Object object : closeOnShutdown) {
            safeClose(object);
        }
    }
    
    private static DataSource createDataSource(DatabaseConfig config) {
        BasicDataSource ds = new BasicDataSource();
        ds.setDriverClassName(config.getDriver());
        ds.setUsername(config.getUsername());
        ds.setPassword(config.getPassword());
        ds.setUrl(config.getUrl() +
                ((config.getOptions() == null || config.getOptions().isEmpty()) ?
                        "" : "?".concat(config.getOptions())));
        return ds;
    }
    
    private static void migrate(DataSource ds) {
        Flyway flyway = new Flyway();
        flyway.setDataSource(ds);
        flyway.migrate();
    }
    
    private static JDA createJDA(String token) throws LoginException, InterruptedException {
        return new JDABuilder(AccountType.BOT)
                .setToken(token)
                .setStatus(OnlineStatus.ONLINE)
                .addEventListener(new LifecycleListener(Akasha::shutdown))
                .setAutoReconnect(true)
                .buildBlocking();
    }
    
    private static void safeClose(Object close) {
        try {
            if (close instanceof AutoCloseable) {
                ((AutoCloseable) close).close();
            }
        }
        catch (Exception ex) {
            log.warn("Shutdown Error: " + ex.getMessage(), ex);
        }
    }
    
    private Akasha() { }
}
