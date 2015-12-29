package jetbrains.buildServer.commitPublisher;

import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;

/**
 * Created by Jonathan on 13/12/2015.
 */
public class GitRepository extends Pair<String, String> {
        public GitRepository(@NotNull String owner, @NotNull String repo) {
            super(owner, repo);
        }

        @NotNull
        public String owner() {
            return first;
        }
        @NotNull
        public String repositoryName() {
            return second;
        }
}
