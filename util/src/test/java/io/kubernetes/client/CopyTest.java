/*
Copyright 2018 The Kubernetes Authors.
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at
    http://www.apache.org/licenses/LICENSE-2.0
Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
package io.kubernetes.client;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import io.kubernetes.client.models.V1ObjectMeta;
import io.kubernetes.client.models.V1Pod;
import io.kubernetes.client.util.ClientBuilder;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/** Tests for the Copy helper class */
public class CopyTest {
  private String namespace;
  private String podName;
  private String[] cmd;

  private ApiClient client;

  private static final int PORT = 8089;
  @Rule public WireMockRule wireMockRule = new WireMockRule(PORT);

  @Before
  public void setup() throws IOException {
    client = new ClientBuilder().setBasePath("http://localhost:" + PORT).build();

    namespace = "default";
    podName = "apod";
  }

  @Test
  public void testUrl() throws IOException, ApiException, InterruptedException {
    Copy copy = new Copy(client);

    V1Pod pod = new V1Pod().metadata(new V1ObjectMeta().name(podName).namespace(namespace));

    //    stubFor(
    //        get(urlPathEqualTo("/api/v1/namespaces/" + namespace + "/pods/" + podName + "/exec"))
    //            .willReturn(
    //                aResponse()
    //                    .withStatus(404)
    //                    .withHeader("Content-Type", "application/json")
    //                    .withBody("{}")));

    InputStream is = copy.copyFileFromPod(pod, "container", "/some/path/to/file");

    //    verify(
    //        getRequestedFor(
    //                urlPathEqualTo("/api/v1/namespaces/" + namespace + "/pods/" + podName +
    // "/exec"))
    //                .withQueryParam("stdin", equalTo("false"))
    //                .withQueryParam("stdout", equalTo("true"))
    //                .withQueryParam("stderr", equalTo("true"))
    //                .withQueryParam("tty", equalTo("false"))
    //                .withQueryParam("command", new AnythingPattern()));
  }

  @Test
  public void testCopyFileToPod() throws IOException, ApiException, InterruptedException {

    File testFile = File.createTempFile("testfile", null);
    testFile.deleteOnExit();

    Copy copy = new Copy(client);

    stubFor(
        get(urlPathEqualTo("/api/v1/namespaces/" + namespace + "/pods/" + podName + "/exec"))
            .willReturn(
                aResponse()
                    .withStatus(404)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{}")));

    // When attempting to write to the process outputstream in copyFileToPod, the
    // WebSocketStreamHandler is in a wait state because no websocket is created by mock, which
    // blocks the main thread. So here we execute the method in a thread.
    Thread t =
        new Thread(
            new Runnable() {
              public void run() {
                try {
                  copy.copyFileToPod(
                      namespace, podName, "", testFile.toPath(), Paths.get("/copied-testfile"));
                } catch (IOException | ApiException ex) {
                  ex.printStackTrace();
                }
              }
            });
    t.start();
    Thread.sleep(2000);
    t.interrupt();

    verify(
        getRequestedFor(
                urlPathEqualTo("/api/v1/namespaces/" + namespace + "/pods/" + podName + "/exec"))
            .withQueryParam("stdin", equalTo("true"))
            .withQueryParam("stdout", equalTo("true"))
            .withQueryParam("stderr", equalTo("true"))
            .withQueryParam("tty", equalTo("false"))
            .withQueryParam("command", equalTo("sh"))
            .withQueryParam("command", equalTo("-c"))
            .withQueryParam("command", equalTo("base64 -d | tar -xmf - -C /")));
  }
}
