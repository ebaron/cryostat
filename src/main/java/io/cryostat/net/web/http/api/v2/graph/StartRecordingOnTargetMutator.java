/*
 * Copyright The Cryostat Authors
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or data
 * (collectively the "Software"), free of charge and under any and all copyright
 * rights in the Software, and any and all patent rights owned or freely
 * licensable by each licensor hereunder covering either (i) the unmodified
 * Software as contributed to or provided by such licensor, or (ii) the Larger
 * Works (as defined below), to deal in both
 *
 * (a) the Software, and
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software (each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 * The above copyright notice and either this complete permission notice or at
 * a minimum a reference to the UPL must be included in all copies or
 * substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package io.cryostat.net.web.http.api.v2.graph;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.inject.Provider;

import org.openjdk.jmc.flightrecorder.configuration.recording.RecordingOptionsBuilder;
import org.openjdk.jmc.rjmx.services.jfr.IRecordingDescriptor;

import io.cryostat.configuration.CredentialsManager;
import io.cryostat.core.templates.TemplateType;
import io.cryostat.jmc.serialization.HyperlinkedSerializableRecordingDescriptor;
import io.cryostat.net.ConnectionDescriptor;
import io.cryostat.net.TargetConnectionManager;
import io.cryostat.net.web.WebServer;
import io.cryostat.platform.discovery.TargetNode;
import io.cryostat.recordings.RecordingOptionsBuilderFactory;
import io.cryostat.recordings.RecordingTargetHelper;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;

class StartRecordingOnTargetMutator
        implements DataFetcher<HyperlinkedSerializableRecordingDescriptor> {

    private final TargetConnectionManager targetConnectionManager;
    private final RecordingTargetHelper recordingTargetHelper;
    private final RecordingOptionsBuilderFactory recordingOptionsBuilderFactory;
    private final CredentialsManager credentialsManager;
    private final Provider<WebServer> webServer;

    @Inject
    StartRecordingOnTargetMutator(
            TargetConnectionManager targetConnectionManager,
            RecordingTargetHelper recordingTargetHelper,
            RecordingOptionsBuilderFactory recordingOptionsBuilderFactory,
            CredentialsManager credentialsManager,
            Provider<WebServer> webServer) {
        this.targetConnectionManager = targetConnectionManager;
        this.recordingTargetHelper = recordingTargetHelper;
        this.recordingOptionsBuilderFactory = recordingOptionsBuilderFactory;
        this.credentialsManager = credentialsManager;
        this.webServer = webServer;
    }

    @Override
    public HyperlinkedSerializableRecordingDescriptor get(DataFetchingEnvironment environment)
            throws Exception {
        TargetNode node = environment.getSource();
        Map<String, Object> settings = environment.getArgument("recording");

        String uri = node.getTarget().getServiceUri().toString();
        ConnectionDescriptor cd =
                new ConnectionDescriptor(uri, credentialsManager.getCredentials(node.getTarget()));
        return targetConnectionManager.executeConnectedTask(
                cd,
                conn -> {
                    RecordingOptionsBuilder builder =
                            recordingOptionsBuilderFactory
                                    .create(conn.getService())
                                    .name((String) settings.get("name"));
                    if (settings.containsKey("duration")) {
                        builder =
                                builder.duration(
                                        TimeUnit.SECONDS.toMillis((Long) settings.get("duration")));
                    }
                    if (settings.containsKey("toDisk")) {
                        builder = builder.toDisk((Boolean) settings.get("toDisk"));
                    }
                    if (settings.containsKey("maxAge")) {
                        builder = builder.maxAge((Long) settings.get("maxAge"));
                    }
                    if (settings.containsKey("maxSize")) {
                        builder = builder.maxSize((Long) settings.get("maxSize"));
                    }
                    IRecordingDescriptor desc =
                            recordingTargetHelper.startRecording(
                                    cd,
                                    builder.build(),
                                    (String) settings.get("template"),
                                    TemplateType.valueOf(
                                            ((String) settings.get("templateType")).toUpperCase()));
                    WebServer ws = webServer.get();
                    return new HyperlinkedSerializableRecordingDescriptor(
                            desc,
                            ws.getDownloadURL(conn, desc.getName()),
                            ws.getReportURL(conn, desc.getName()));
                },
                true);
    }
}
