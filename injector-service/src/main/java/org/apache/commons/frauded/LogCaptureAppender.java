package org.apache.commons.frauded;

import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;

public class LogCaptureAppender extends AbstractAppender {
  private final Agent agent;

  public LogCaptureAppender(Agent agent) {
    super("FraudConsole", null, null);
    this.agent = agent;
  }

  @Override
  public void append(LogEvent event) {
    String msg = event.getMessage().getFormattedMessage();
    if (msg != null && !msg.isEmpty()) agent.sendConsole(msg);
  }
}
