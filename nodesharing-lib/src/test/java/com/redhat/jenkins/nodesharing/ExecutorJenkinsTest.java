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
package com.redhat.jenkins.nodesharing;

import org.junit.Test;

import java.net.URL;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class ExecutorJenkinsTest {

    private static final String VALID_URL = "https://as.df:8080/orchestrator/";
    private static final String VALID_NAME = "as.df";
    private static final String CREDENTIALS_ID = "some-creds";

    @Test(expected = IllegalArgumentException.class)
    public void notAnUrl() throws Exception {
        new ExecutorJenkins("not an URL", VALID_NAME);
    }

    @Test(expected = IllegalArgumentException.class)
    public void unsafeName() throws Exception {
        new ExecutorJenkins(VALID_URL, "Robert'; drop table STUDENTS;--");
    }

    @Test
    public void basics() throws Exception {
        ExecutorJenkins ej = new ExecutorJenkins(VALID_URL, VALID_NAME);

        assertEquals(VALID_NAME, ej.getName());
        assertEquals(new URL(VALID_URL), ej.getUrl());
        assertEquals(new URL("https://as.df:8080/orchestrator/"), ej.getEndpointUrl());
    }

    @Test
    public void equality() throws Exception {
        ExecutorJenkins valid = new ExecutorJenkins(VALID_URL, VALID_NAME);
        assertEquals(valid, new ExecutorJenkins(VALID_URL, VALID_NAME));
        assertNotEquals(valid, new ExecutorJenkins(VALID_URL + "a", VALID_NAME));
        assertNotEquals(valid, new ExecutorJenkins(VALID_URL, VALID_NAME + "a"));
        assertNotEquals(valid, new ExecutorJenkins(VALID_URL, VALID_NAME + "a", CREDENTIALS_ID));
        valid = new ExecutorJenkins(VALID_URL, VALID_NAME, CREDENTIALS_ID);
        assertNotEquals(valid, new ExecutorJenkins(VALID_URL, VALID_NAME + "a"));
        assertEquals(valid, new ExecutorJenkins(VALID_URL, VALID_NAME, CREDENTIALS_ID));
    }

//    @Test
//    public void inferCloudName() throws Exception {
//        assertEquals("github.com_jenkinsci_node-sharing-plugin", ExecutorJenkins.inferCloudName("https://github.com/jenkinsci/node-sharing-plugin.git"));
//        assertEquals("git_github.com_jenkinsci_node-sharing-plugin", ExecutorJenkins.inferCloudName("git@github.com:jenkinsci/node-sharing-plugin.git"));
//    }
}
