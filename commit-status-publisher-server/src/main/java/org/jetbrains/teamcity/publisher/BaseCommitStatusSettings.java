package org.jetbrains.teamcity.publisher;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public abstract class BaseCommitStatusSettings implements CommitStatusPublisherSettings {
    @Override
    @Nullable
    public Collection<String> getMandatoryParameters() {
        return null;
    }

    @Override
    @Nullable
    public Map<String, String> getDefaultParameters() {
        return null;
    }

    @Override
    @Nullable
    public Map<String, String> getParameterUpgrades(@NotNull Map<String, String> params) {
        Map<String, String> paramChanges = new HashMap<String, String>();
        Map<String, String> defaultParams = getDefaultParameters();
        Collection<String> mandatoryParams = getMandatoryParameters();

        // Are we missing a mandatory parameter that we know a default value for?
        if (mandatoryParams != null && defaultParams != null) {
            for (String paramName : mandatoryParams) {
                if (!params.containsKey(paramName) && defaultParams.containsKey(paramName)) {
                    paramChanges.put(paramName, defaultParams.get(paramName));
                }
            }
        }

        if (paramChanges.isEmpty()) {
            return null;
        }
        return paramChanges;
    }

    /**
     * Inject new mandatory properties into an existing property list before
     * creating a publisher instance.
     *
     * This is necessary for properties that have been added to our code since
     * the properties for a specific Build Feature instance were first created
     * by the user.
     */

    @NotNull
    protected Map<String, String> getUpdatedParametersForPublisher(@NotNull final Map<String, String> params) {
        Map<String, String> updates = getParameterUpgrades(params);
        if (updates == null) {
            return params;
        }

        // Note that the supplied `params` map is unmodifiable.
        Map<String, String> updatedParams = new HashMap<String, String>(params);

        for (Map.Entry<String, String> update: updates.entrySet()) {
            updatedParams.put(update.getKey(), update.getValue());
        }

        return updatedParams;
    }
}
