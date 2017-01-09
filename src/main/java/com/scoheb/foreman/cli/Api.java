package com.scoheb.foreman.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.scoheb.foreman.cli.model.Architecture;
import com.scoheb.foreman.cli.model.Domain;
import com.scoheb.foreman.cli.model.Environment;
import com.scoheb.foreman.cli.model.Host;
import com.scoheb.foreman.cli.model.Hostgroup;
import com.scoheb.foreman.cli.model.Medium;
import com.scoheb.foreman.cli.model.OperatingSystem;
import com.scoheb.foreman.cli.model.PTable;
import com.scoheb.foreman.cli.model.Parameter;
import org.apache.log4j.Logger;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.authentication.HttpAuthenticationFeature;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by shebert on 06/01/17.
 */
public class Api {
    private static final String V2 = "/v2";
    private String server;
    private String user;
    private String password;
    private WebTarget base;
    private Logger LOGGER = Logger.getLogger(Api.class);

    public Api(String server, String user, String password) {
        this.server = server;
        this.user = user;
        this.password = password;
        initClient();
    }

    private void initClient() {
        ClientConfig clientConfig = new ClientConfig();
        HttpAuthenticationFeature feature = HttpAuthenticationFeature.basic(user, password);
        clientConfig.register(feature);
        Client client = ClientBuilder.newClient(clientConfig);
        String s = server;
        //remove trailing slash
        if (s.endsWith("/")) {
            s = s.substring(0, s.length() - 1);
        }
        //remove api
        if (s.endsWith("v2")) {
            s = s.substring(0, s.length() - 2);
        }
        base = client
                .target(s);
    }

    public Domain createDomain(String name) {
        Domain domain = getDomain(name);
        if (domain != null) {
            LOGGER.info("Domain " + name + " already exists...");
            return domain;
        }
        JsonObject innerObject = new JsonObject();
        innerObject.addProperty("name", name);

        JsonObject jsonObject = new JsonObject();
        jsonObject.add("domain", innerObject);
        String json = jsonObject.toString();

        return (Domain)createObject("domains", Domain.class, json);
    }

    private Object createObject(String objectType, Class className, String json) {
        Response response =
                base.path(V2 + "/" + objectType).request(MediaType.APPLICATION_JSON)
                        .post(Entity.entity(json, MediaType.APPLICATION_JSON));
        String responseAsString = response.readEntity(String.class);
        LOGGER.info(responseAsString);
        if (Response.Status.fromStatusCode(response.getStatus()) == Response.Status.CREATED ||
                Response.Status.fromStatusCode(response.getStatus()) == Response.Status.OK  ) {
            Gson gson = new Gson();
            return gson.fromJson(responseAsString, className);
        } else {
            LOGGER.error("Creating " + objectType
                    + " returned code " + response.getStatus() + ".");
            return null;
        }
    }

    public Domain getDomain(String name) {
        Response response = base.path(V2 + "/domains")
                .queryParam("search", "name = " + name)
                .request(MediaType.APPLICATION_JSON).get();
        String result = getResultString(response, "domains");
        Gson gson = new Gson();
        return gson.fromJson(result, Domain.class);
    }

    public Environment getEnvironment(String name) {
        Response response = base.path(V2 + "/environments")
                .queryParam("search", "name = " + name)
                .request(MediaType.APPLICATION_JSON).get();
        String result = getResultString(response, "environments");
        Gson gson = new Gson();
        return gson.fromJson(result, Environment.class);
    }

    private String getResultString(Response response,
                                   String objectType) {
        return getResultString(response, objectType, true);

    }

    private String getResultString(Response response,
                                         String objectType, boolean firstElementOnly) {
        String responseAsString = response.readEntity(String.class);
        LOGGER.debug(responseAsString);
        if (Response.Status.fromStatusCode(response.getStatus()) == Response.Status.OK) {
            try {
                ObjectMapper mapper = new ObjectMapper();
                JsonNode json = mapper.readValue(responseAsString, JsonNode.class);
                JsonNode results = json.get("results");
                if (results != null && results.isArray()) {
                    if (firstElementOnly) {
                        JsonNode firstElem = results.get(0);
                        if (firstElem == null) {
                            return null;
                        }
                        return firstElem.toString();
                    } else {
                        return results.toString();
                    }
                }
            } catch (Exception e) {
                LOGGER.error("Unhandled exception getting object " + objectType + ": ", e);
                e.printStackTrace();
            }
        } else {
            LOGGER.error("Retrieving " + objectType
                    + " returned code " + response.getStatus() + ".");
        }
        return null;
    }

    public Environment createEnvironment(String name) {
        Environment env = getEnvironment(name);
        if (env != null) {
            LOGGER.info("Environment " + name + " already exists...");
            return env;
        }
        JsonObject innerObject = new JsonObject();
        innerObject.addProperty("name", name);

        JsonObject jsonObject = new JsonObject();
        jsonObject.add("environment", innerObject);
        String json = jsonObject.toString();

        return (Environment)createObject("environments", Environment.class, json);
    }

    public Architecture getArchitecture(String name) {
        Response response = base.path(V2 + "/architectures")
                .queryParam("search", "name = " + name)
                .request(MediaType.APPLICATION_JSON).get();
        String result = getResultString(response, "architectures");
        Gson gson = new Gson();
        return gson.fromJson(result, Architecture.class);
    }

    public Medium getMedium(String name) {
        Response response = base.path(V2 + "/media")
                .queryParam("search", "name = " + "\"" + name + "\"")
                .request(MediaType.APPLICATION_JSON).get();
        String result = getResultString(response, "media");
        Gson gson = new Gson();
        return gson.fromJson(result, Medium.class);
    }

    public PTable getPTable(String name) {
        Response response = base.path(V2 + "/ptables")
                .queryParam("search", "name = " + "\"" + name + "\"")
                .request(MediaType.APPLICATION_JSON).get();
        String result = getResultString(response, "ptables");
        Gson gson = new Gson();
        return gson.fromJson(result, PTable.class);
    }

    public OperatingSystem getOperatingSystem(String name) {
        Response response = base.path(V2 + "/operatingsystems")
                .queryParam("search", "name = " + "\"" + name + "\"")
                .request(MediaType.APPLICATION_JSON).get();
        String result = getResultString(response, "operatingsystems");
        Gson gson = new Gson();
        return gson.fromJson(result, OperatingSystem.class);
    }

    public Hostgroup getHostGroup(String name) {
        Response response = base.path(V2 + "/hostgroups")
                .queryParam("search", "name = " + "\"" + name + "\"")
                .request(MediaType.APPLICATION_JSON).get();
        String result = getResultString(response, "hostgroups");
        Gson gson = new Gson();
        return gson.fromJson(result, Hostgroup.class);
    }

    public Host getHost(String name) {
        Response response = base.path(V2 + "/hosts/" + name)
                .request(MediaType.APPLICATION_JSON).get();
        String responseAsString = response.readEntity(String.class);
        LOGGER.debug(responseAsString);
        if (Response.Status.fromStatusCode(response.getStatus()) == Response.Status.OK  ) {
            Gson gson = new Gson();
            return gson.fromJson(responseAsString, Host.class);
        }
        return null;
    }

    public OperatingSystem createOperatingSystem(String name,
                                                 String major,
                                                 String minor,
                                                 int arch_id,
                                                 int media_id,
                                                 int ptable_id,
                                                 String family) {
        OperatingSystem os = getOperatingSystem(name);
        if (os != null) {
            LOGGER.info("OperatingSystem " + name + " already exists...");
            return os;
        }
        JsonObject innerObject = new JsonObject();
        innerObject.addProperty("name", name);
        innerObject.addProperty("major", major);
        innerObject.addProperty("minor", minor);
        innerObject.addProperty("architecture_ids", arch_id);
        innerObject.addProperty("medium_ids", media_id);
        innerObject.addProperty("ptable_ids", ptable_id);
        innerObject.addProperty("family", family);

        JsonObject jsonObject = new JsonObject();
        jsonObject.add("operatingsystem", innerObject);
        String json = jsonObject.toString();

        return (OperatingSystem)createObject("operatingsystems", OperatingSystem.class, json);
    }

    public Hostgroup createHostGroup(String name,
                                     int envId,
                                     int domainID,
                                     int archId,
                                     int osId,
                                     int mediaId,
                                     int ptableId,
                                     String rootPass) {
        Hostgroup hg = getHostGroup(name);
        if (hg != null) {
            LOGGER.info("Hostgroup " + name + " already exists...");
            return hg;
        }

        JsonObject innerObject = new JsonObject();
        innerObject.addProperty("name", name);
        innerObject.addProperty("environment_id", envId);
        innerObject.addProperty("domain_id", domainID);
        innerObject.addProperty("architecture_id", archId);
        innerObject.addProperty("operatingsystem_id", osId);
        innerObject.addProperty("medium_id", mediaId);
        innerObject.addProperty("ptable_id", ptableId);
        innerObject.addProperty("root_pass", rootPass);

        JsonObject jsonObject = new JsonObject();
        jsonObject.add("hostgroup", innerObject);
        String json = jsonObject.toString();

        return (Hostgroup)createObject("hostgroups", Hostgroup.class, json);
    }

    public Host createHost(String name,
                           String ip,
                           Domain domain,
                           int hostgroupid,
                           int archId,
                           int osId,
                           int mediaId,
                           int ptableId,
                           int envId,
                           String rootPass) {
        Host host = getHost(name + "." + domain.name);
        if (host != null) {
            LOGGER.info("Host " + name + " already exists...");
            return host;
        }

        JsonObject innerObject = new JsonObject();
        innerObject.addProperty("name", name + "." + domain.name);
        innerObject.addProperty("ip", ip);
        innerObject.addProperty("environment_id", envId);
        innerObject.addProperty("domain_id", domain.id);
        innerObject.addProperty("hostgroup_id", hostgroupid);
        innerObject.addProperty("architecture_id", archId);
        innerObject.addProperty("operatingsystem_id", osId);
        innerObject.addProperty("medium_id", mediaId);
        innerObject.addProperty("ptable_id", ptableId);
        innerObject.addProperty("root_pass", rootPass);
        innerObject.addProperty("mac", "50:7b:9d:4d:f1:12");

        JsonObject jsonObject = new JsonObject();
        jsonObject.add("host", innerObject);
        String json = jsonObject.toString();

        return (Host)createObject("hosts", Host.class, json);
    }

    public Host addHostParameter(Host host, Parameter parameter) {
        JsonArray params = new JsonArray();
        JsonObject paramObject = new JsonObject();
        paramObject.addProperty("name", parameter.name);
        paramObject.addProperty("value", parameter.value);
        params.add(paramObject);

        JsonObject innerObject = new JsonObject();
        innerObject.addProperty("name", host.name);
        innerObject.addProperty("ip", host.ip);
        innerObject.add("host_parameters_attributes", params);

        JsonObject jsonObject = new JsonObject();
        jsonObject.add("host", innerObject);
        String json = jsonObject.toString();

        Response response =
                base.path(V2 + "/hosts/" + host.id).request(MediaType.APPLICATION_JSON)
                        .put(Entity.entity(json, MediaType.APPLICATION_JSON));
        String responseAsString = response.readEntity(String.class);
        LOGGER.debug(responseAsString);
        if (Response.Status.fromStatusCode(response.getStatus()) == Response.Status.CREATED ||
                Response.Status.fromStatusCode(response.getStatus()) == Response.Status.OK  ) {
            Gson gson = new Gson();
            return gson.fromJson(responseAsString, Host.class);
        } else {
            LOGGER.error("Updating host returned code " + response.getStatus() + ".");
            return null;
        }
    }

    public Parameter updateHostParameter(Host host, Parameter parameter) {

        Parameter existing = getHostParameter(host, parameter.name);
        if (existing == null) {
            Host hostAddedParam = addHostParameter(host, parameter);
            return hostAddedParam.getParameterValue(parameter.name);
        }
        if (existing.value.equals(parameter.value)) {
            LOGGER.info("Value for parameter " + parameter.name + " already set to " + parameter.value);
            return existing;
        }
        parameter.id = existing.id;


        JsonObject innerObject = new JsonObject();
        innerObject.addProperty("name", parameter.name);
        innerObject.addProperty("value", parameter.value);

        JsonObject jsonObject = new JsonObject();
        jsonObject.add("parameter", innerObject);
        String json = jsonObject.toString();

        Response response =
                base.path(V2 + "/hosts/" + host.id + "/parameters/"
                        + parameter.id).request(MediaType.APPLICATION_JSON)
                        .put(Entity.entity(json, MediaType.APPLICATION_JSON));
        String responseAsString = response.readEntity(String.class);
        LOGGER.debug(responseAsString);
        if (Response.Status.fromStatusCode(response.getStatus()) == Response.Status.CREATED ||
                Response.Status.fromStatusCode(response.getStatus()) == Response.Status.OK  ) {
            Gson gson = new Gson();
            return gson.fromJson(responseAsString, Parameter.class);
        } else {
            LOGGER.error("Updating host parameter returned code " + response.getStatus() + ".");
            return null;
        }
    }

    public Parameter getHostParameter(Host host, String parameterName) {
        Response response = base.path(V2 + "/hosts/" + host.name)
                .request(MediaType.APPLICATION_JSON).get();
        String responseAsString = response.readEntity(String.class);
        LOGGER.debug(responseAsString);
        Gson gson = new Gson();
        Host updatedHost = gson.fromJson(responseAsString, Host.class);
        return updatedHost.getParameterValue(parameterName);
    }

    public List<Host> getHosts(Hostgroup hostgroup) {
        Type listType = new TypeToken<ArrayList<Host>>(){}.getType();
        Response response = base.path(V2 + "/hostgroups/" + hostgroup.name + "/hosts")
                .request(MediaType.APPLICATION_JSON).get();
        String result = getResultString(response, "hosts", false);
        Gson gson = new Gson();
        return gson.fromJson(result, listType);
    }

    public List<Host> getHosts(Environment environment) {
        Type listType = new TypeToken<ArrayList<Host>>(){}.getType();
        Response response = base.path(V2 + "/environments/" + environment.name + "/hosts")
                .request(MediaType.APPLICATION_JSON).get();
        String result = getResultString(response, "hosts", false);
        Gson gson = new Gson();
        return gson.fromJson(result, listType);
    }

    public static String fixValue(Parameter param) {
        String val = "";
        if (param != null) {
            val = param.value;
        }
        return val;
    }

    public void releaseHost(Host h) {
        Response response = base.path("/hosts_release")
                .queryParam("search", "name = " + h.name)
                .request(MediaType.APPLICATION_JSON).get();
        String responseAsString = response.readEntity(String.class);
        LOGGER.info(responseAsString);
    }

    public void reserveHost(Host h, String reserveReason) {
        String reservation = fixValue(this.getHostParameter(h, "RESERVED"));
        if (reservation.equals("false")) {
            Response response = base.path("/hosts_reserve")
                    .queryParam("search", "name = " + h.name)
                    .queryParam("reason", reserveReason)
                    .request(MediaType.APPLICATION_JSON).get();
            String responseAsString = response.readEntity(String.class);
            LOGGER.info(response.getStatus());
            LOGGER.info(responseAsString);
        } else {
            LOGGER.error("Already RESERVED by: " + reservation);
        }
    }
}
