package jetbrains.buildServer.commitPublisher;

import com.intellij.openapi.diagnostic.Logger;
import java.util.Stack;
import org.apache.log4j.Level;
import org.jetbrains.annotations.NonNls;

/**
 * @author anton.zamolotskikh, 20/02/17.
 */
class PublisherLogger extends Logger {

  private final Stack<String> entries = new Stack<String>();

  String popLast() {
    return entries.pop();
  }

  @Override
  public boolean isDebugEnabled() {
    return false;
  }

  @Override
  public void debug(@NonNls final String message) {
    entries.push("DEBUG: " + message);
  }

  @Override
  public void debug(@NonNls final String message, final Throwable t) {
    debug(message);
  }

  @Override
  public void error(@NonNls final String message, final Throwable t, @NonNls final String... details) {
    entries.push("ERROR: " + message);
  }

  @Override
  public void info(@NonNls final String message) {
    entries.push("INFO: " + message);
  }

  @Override
  public void info(@NonNls final String message, final Throwable t) {
    info(message);
  }

  @Override
  public void warn(@NonNls final String message, final Throwable t) {
    entries.push("WARN: " + message);
  }

  @Override
  public void setLevel(final Level level) {
  }
}
