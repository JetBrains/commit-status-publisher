

/*
 * Copyright 2000-2024 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
  public boolean isTraceEnabled() {
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