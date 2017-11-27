package com.redhat.jenkins.nodesharingfrontend;

import java.util.logging.Logger;

import com.redhat.jenkins.nodesharing.transport.ReturnNodeRequest;
import org.jenkinsci.plugins.resourcedisposer.Disposable;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

/**
 * Created by shebert on 27/09/16.
 */
public class DisposableImpl implements Disposable {

    private final String cloudName;
    private final String name;
    private static final Logger LOGGER = Logger.getLogger(DisposableImpl.class.getName());

    public DisposableImpl(String cloudName, String name) {
        this.cloudName = cloudName;
        this.name = name;
    }

    @Nonnull
    @Override
    public State dispose() throws Exception {
        SharedNodeCloud cloud = SharedNodeCloud.getByName(cloudName);
        if (cloud != null) {
            LOGGER.finer("Attempt to release the node: " + name);
            try {
                cloud.getApi().returnNode(name, ReturnNodeRequest.Status.OK); // TODO specify status
            } catch (Exception e) {
                // According Foreman Docs, 404 code means host doesn't exist or isn't reserved now
                // We can destroy the Disposable item in that case
                if (!e.getMessage().contains("returned code 404")) {
                    // TODO: target particular type
                    e.printStackTrace();
                    throw e;
                }
            }
            LOGGER.finer("[COMPLETED] Attempt to release the node: " + name);
            return State.PURGED;
        }
        String msg = "Foreman cloud instance '" + cloudName + "' not found.";
        LOGGER.warning(msg);
        return new State.Failed(msg);
    }

    @Nonnull
    @Override
    public String getDisplayName() {
        return "Dispose Foreman Shared Node Cloud " + cloudName + " - Shared Node: " + name;
    }

    @CheckForNull
    public String getCloudName() {
        return cloudName;
    }

    @CheckForNull
    public String getName() {
        return name;
    }
}
