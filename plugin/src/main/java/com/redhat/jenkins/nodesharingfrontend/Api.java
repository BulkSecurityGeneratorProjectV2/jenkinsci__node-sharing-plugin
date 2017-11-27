/*
 * The MIT License
 *
 * Copyright (c) Red Hat, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.redhat.jenkins.nodesharingfrontend;

import com.google.gson.Gson;
import com.redhat.jenkins.nodesharing.ActionFailed;
import com.redhat.jenkins.nodesharing.Communication;
import com.redhat.jenkins.nodesharing.ConfigRepo;
import com.redhat.jenkins.nodesharing.Workload;
import com.redhat.jenkins.nodesharing.transport.DiscoverRequest;
import com.redhat.jenkins.nodesharing.transport.DiscoverResponse;
import com.redhat.jenkins.nodesharing.transport.ExecutorEntity;
import com.redhat.jenkins.nodesharing.transport.ReturnNodeRequest;
import hudson.model.Node;
import hudson.model.Queue;
import jenkins.model.Jenkins;
import jenkins.model.JenkinsLocationConfiguration;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.ClientProperties;
import org.glassfish.jersey.jackson.JacksonFeature;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.interceptor.RequirePOST;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.ws.rs.NotSupportedException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * Receive and send REST commands from/to Orchestrator Jenkins.
 */
@Restricted(NoExternalUse.class)
// TODO Check permission
public class Api {

    private static final Logger LOGGER = Logger.getLogger(Api.class.getName());
    private final @Nonnull ExecutorEntity.Fingerprint fingerprint;

    private WebTarget base = null;

    private static final String PROPERTIES_FILE = "nodesharingfrontend.properties";
    private static final String PROPERTY_VERSION = "version";
    private Properties properties = null;

    private static final String ORCHESTRATOR_URI = "node-sharing-orchestrator";
    private static final String ORCHESTRATOR_REPORTWORKLOAD = "reportWorkload";
    private static final String ORCHESTRATOR_REPORTWORKLOAD_URI = ORCHESTRATOR_URI+"/"+ORCHESTRATOR_REPORTWORKLOAD;

    public Api(@Nonnull final ConfigRepo.Snapshot snapshot, @Nonnull final String configRepoUrl) {
        this.fingerprint = new ExecutorEntity.Fingerprint(
                configRepoUrl,
                getProperties().getProperty("version"),
                snapshot.getJenkins(JenkinsLocationConfiguration.get().getUrl()).getName()
        );

        ClientConfig clientConfig = new ClientConfig();

        // TODO HTTP autentization
        //HttpAuthenticationFeature feature = HttpAuthenticationFeature.basic(user, Secret.toString(password));
        //clientConfig.register(feature);

        clientConfig.register(JacksonFeature.class);
        Client client = ClientBuilder.newClient(clientConfig);

        // Define a quite defensive timeouts
        client.property(ClientProperties.CONNECT_TIMEOUT, 60000);   // 60s
        client.property(ClientProperties.READ_TIMEOUT,    300000);  // 5m
        base = client.target(snapshot.getOrchestratorUrl());
    }

    /**
     * Get properties.
     *
     * @return Properties.
     */
    @Nonnull
    private Properties getProperties() {
        if(properties == null) {
            properties = new Properties();
            try {
                properties.load(this.getClass().getClassLoader().getResourceAsStream(PROPERTIES_FILE));
            } catch (IOException e) {
                properties = new Properties();
            }
        }
        return properties;
    }

    /**
     * Do GET HTTP request on target.
     *
     * @param target The request.
     * @return Server response.
     */
    @Nonnull
    public Response doGetRequest(@Nonnull final WebTarget target) {
        return doGetRequest(target, Response.Status.OK);
    }

    /**
     * Do GET HTTP request on target and throws if response doesn't match the expectation.
     *
     * @param target The request.
     * @param status Expected status.
     *
     * @return Server response.
     */
    @Nonnull
    public Response doGetRequest(@Nonnull final WebTarget target, @Nonnull final Response.Status status) {
        Response response = Response.serverError().entity("error").build();
        try {
            response = target.request(MediaType.APPLICATION_JSON).get();
        } catch (Exception e) {
            LOGGER.severe(e.getMessage());
            throw new ActionFailed.CommunicationError("Performing GET request '" + target.toString()
                    + "' returns unexpected response status '" + response.getStatus()
                    + "' [" + response.readEntity(String.class) + "]");
        }
        return response;
    }

    /**
     * Do POST HTTP request on target.
     *
     * @param target The request.
     * @param json JSON string.
     *
     * @return Response from the server.
     */
    @Nonnull
    public Response doPostRequest(@Nonnull final WebTarget target, @Nonnull final String json) {
        return doPostRequest(target, json, Response.Status.OK);

    }

    /**
     * Do POST HTTP request on target and throws exception if response doesn't match the expectation.
     *
     * @param target The request.
     * @param json JSON string.
     * @param status Expected status.
     *
     * @return Response from the server.
     */
    @Nonnull
    public Response doPostRequest(@Nonnull final WebTarget target, @Nonnull final String json,
                                        @Nonnull final Response.Status status) {
        Response response = target.queryParam(PROPERTY_VERSION, getProperties().getProperty(PROPERTY_VERSION, ""))
                .request(MediaType.APPLICATION_JSON_TYPE)
                .post(Entity.text(json));
        if (!status.equals(Response.Status.fromStatusCode(response.getStatus()))) {
            throw new ActionFailed.CommunicationError("Performing POST request '" + target.toString()
                    + "' returns unexpected response status '" + response.getStatus()
                    + "' [" + response.readEntity(String.class) + "]");
        }
        return response;
    }


    //// Outgoing

    /**
     * Query Executor Jenkins to report the status of shared node.
     *
     * @param name Name of the node to be queried.
     * @return Node status.
     */
    @CheckForNull
    // TODO What is it what we are REALLY communicating by throwing/returning int on POST level?
    public Object nodeStatus(@Nonnull @QueryParameter("name") final String name) {
        if(name == null) {
            throw new ActionFailed.CommunicationError("Node name cannot be 'null'!");
        }
        Communication.NodeState status = Communication.NodeState.NOT_FOUND;
        Node node = Jenkins.getActiveInstance().getNode(name);
        if (node != null) {
            // TODO Extract the current state
            status = Communication.NodeState.FOUND;
        }
        return status.ordinal();
    }

    /**
     * Query Executor Jenkins to report the status of executed item.
     *
     * @param id ID of the run to be queried.
     * @return Item status.
     */
    @CheckForNull
    // TODO What is it what we are REALLY communicating by throwing/returning int on POST level?
    public Object runStatus(@Nonnull @QueryParameter("id") final String id) {
        if (id == null) {
            throw new ActionFailed.CommunicationError("Work id cannot be 'null'!");
        }
        long runId;
        try {
            runId = Long.parseLong(id);
        } catch (NumberFormatException e) {
            throw new ActionFailed.CommunicationError("Invalid id value '" + id + "'", e);
        }
        Communication.RunState status = Communication.RunState.NOT_FOUND;
        Queue.Item item = Jenkins.getActiveInstance().getQueue().getItem(runId);
        if (item != null) {
            // TODO Extract run current state
            status = Communication.RunState.FOUND;
        }
        return status.ordinal();
    }

    /**
     * Put the queue items to Orchestrator
     */
    public Response.Status doReportWorkload(@Nonnull final List <Queue.Item> items) {

/*
        Set<LabelAtom> sla = new TreeSet<LabelAtom>();

        // TODO Get List of provided labels
        Set<LabelAtom> sla_tmp = new TreeSet<LabelAtom>();
        sla_tmp.add(new LabelAtom("foo"));
        sla_tmp.add(new LabelAtom("bar"));
        for(LabelAtom la : sla_tmp) {
            sla.add(la);
        }
        sla_tmp = new TreeSet<LabelAtom>();
        sla_tmp.add(new LabelAtom("test"));
        for(LabelAtom la : sla_tmp) {
            sla.add(la);
        }
        // TODO Remove above with proper impl.

        List<Queue.Item> qi = new ArrayList<Queue.Item>();

        for(Queue.Item i : Jenkins.getInstance().getQueue().getItems()) {
            if(i.getAssignedLabel().matches(sla)) {
                 qi.add(i);
            }
        }
*/

        final Workload workItems = new Workload();
        for(Queue.Item item : items) {
            workItems.addItem(item);
        }

        System.out.println("Frontend: " + new Gson().toJson(workItems));
        return Response.Status.fromStatusCode(
                doPostRequest(base.path(ORCHESTRATOR_REPORTWORKLOAD_URI), new Gson().toJson(workItems)).getStatus());
    }

    /**
     * Request to discover the state of the Orchestrator.
     *
     * @return Discovery result.
     */
    public DiscoverResponse discover() {
        DiscoverRequest request = new DiscoverRequest(fingerprint);
        Entity<String> text = Entity.text(request.toString());
        InputStream response = base.path(ORCHESTRATOR_URI + "/discover")
                .request(MediaType.APPLICATION_JSON_TYPE)
                .post(text, InputStream.class)
        ;

        // TODO Check status code
        return com.redhat.jenkins.nodesharing.transport.Entity.fromInputStream(response, DiscoverResponse.class);
    }

    /**
     * Send request to return node. No response needed.
     */
    public void returnNode(@Nonnull final String name, @Nonnull ReturnNodeRequest.Status status) {
        ReturnNodeRequest request = new ReturnNodeRequest(fingerprint, name, status);

        Entity<String> text = Entity.text(request.toString());
        Response response = base.path(ORCHESTRATOR_URI + "/returnNode")
                .request(MediaType.APPLICATION_JSON_TYPE)
                .post(text)
        ;

        // TODO check status
    }

    //// Incoming

    /**
     * Request to execute #Item from the queue
     */
    @RequirePOST
    public void doExecution(@Nonnull @QueryParameter final String nodeName,
                            @Nonnull @QueryParameter final String id) {
        // TODO Create a Node based on the info and execute the Item
    }

    /**
     * Immediately return node to orchestrator. (Nice to have feature)
     *
     * @param name Name of the node to be returned.
     */
    @RequirePOST
    public void doReturnNode(@Nonnull @QueryParameter("name") final String name) {
        throw new NotSupportedException();
/*
        Computer c = Jenkins.getInstance().getComputer(name);
        if (!(c instanceof SharedComputer)) {
            // TODO computer not reservable
            return;
        }
        SharedComputer computer = (SharedComputer) c;
        ReservationTask.ReservationExecutable executable = computer.getReservation();
        if (executable == null) {
            // TODO computer not reserved
            return;
        }
        // TODO The owner parameter is in no way sufficient proof the client is authorized to release this
        executable.complete(owner, state);
*/
    }
}
