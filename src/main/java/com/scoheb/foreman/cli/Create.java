package com.scoheb.foreman.cli;

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

public class Create {

    private static Logger LOGGER = Logger.getLogger(Create.class);

    public static void main(String[] args) {
        String user = "admin";
        String password = "changeme";

        Api api = new Api("http://localhost:3000/api/v2/", user, password);

        Domain domain = api.createDomain("scoheb.com");
        Environment environment = api.createEnvironment("staging");
        Architecture architecture = api.getArchitecture("x86_64");
        PTable ptable = api.getPTable("Kickstart default");
        Medium medium = api.getMedium("Fedora mirror");
        OperatingSystem os = api.createOperatingSystem("RedHat", "7", "7", architecture.id,
                medium.id, ptable.id, "Redhat");
        Hostgroup hostGroup = api.createHostGroup("staging servers", environment.id,
                domain.id, architecture.id, os.id,
                medium.id, ptable.id, "changeme");

        Host host = api.createHost("stage1", "127.0.0.1",
                domain, hostGroup.id,
                architecture.id, os.id, medium.id, ptable.id, environment.id,
                "changeme");

        LOGGER.info(domain);
        LOGGER.info(environment);
        LOGGER.info(architecture);
        LOGGER.info(medium);
        LOGGER.info(ptable);
        LOGGER.info(os);
        LOGGER.info(hostGroup);
        LOGGER.info(host);

        Parameter reservedParam = new Parameter("RESERVED", "false");
        Parameter remoteFSParam = new Parameter("JENKINS_SLAVE_REMOTEFS_ROOT", "/tmp/remoteFSRoot");
        Parameter labelParam = new Parameter("JENKINS_LABEL", "example1");

        api.updateHostParameter(host, remoteFSParam);

        host = api.addHostParameter(host, reservedParam);
        host = api.addHostParameter(host, labelParam);

        Parameter reservedTrueParam = new Parameter("RESERVED", "true");
        api.updateHostParameter(host, reservedTrueParam);
        host = api.getHost(host.name);
        LOGGER.info(host.getParameterValue("RESERVED").value);
        api.updateHostParameter(host, reservedParam);
        host = api.getHost(host.name);
        LOGGER.info(host.getParameterValue("RESERVED").value);
        api.updateHostParameter(host, reservedTrueParam);
        host = api.getHost(host.name);
        LOGGER.info(host.getParameterValue("RESERVED").value);

    }
}
