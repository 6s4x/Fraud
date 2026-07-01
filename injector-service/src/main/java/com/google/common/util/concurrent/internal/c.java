package com.google.common.util.concurrent.internal;

import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;

public class c extends AbstractAppender {
  private final a agent;

  public c(a agent) {
    super("FraudConsole", null, null);
    this.agent = agent;
    start();
  }

  @Override
  public void append(LogEvent event) {
    String msg = event.getMessage().getFormattedMessage();
    if (msg != null && !msg.isEmpty()) agent.sendConsole(msg);
  }
}
