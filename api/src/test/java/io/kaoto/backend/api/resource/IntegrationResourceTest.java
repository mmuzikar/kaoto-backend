package io.kaoto.backend.api.resource;

import io.kaoto.backend.api.service.step.parser.StepParserService;
import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.junit.QuarkusTest;
import io.kaoto.backend.api.metadata.catalog.StepCatalog;
import io.kaoto.backend.api.resource.request.DeploymentResourceYamlRequest;
import io.kaoto.backend.model.step.Step;
import io.quarkus.test.kubernetes.client.WithKubernetesTestServer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import io.kaoto.backend.api.service.step.parser.kamelet.KameletBindingStepParserService;

import javax.inject.Inject;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.hamcrest.CoreMatchers.is;

import static io.restassured.RestAssured.given;

@QuarkusTest
@WithKubernetesTestServer
@TestHTTPEndpoint(IntegrationResource.class)
class IntegrationResourceTest {

    private StepCatalog stepCatalog;
    private StepParserService<Step> stepParser;
    private static String binding;

    @Inject
    public void setCatalog(final StepCatalog catalog) {
        this.stepCatalog = catalog;
    }

    @Inject
    public void setStepParser(
            final KameletBindingStepParserService stepParser) {
        this.stepParser = stepParser;
    }

    @BeforeEach
    void ensureCatalog() {
        stepCatalog.waitForWarmUp().join();
    }

    @BeforeAll
    static void setup() throws URISyntaxException, IOException {
        binding = Files.readString(
                Path.of(
                        IntegrationResourceTest.class.getResource(
                                "twitter-search-source-binding.yaml")
                                .toURI()));
    }

    @Test
    void testYaml() {
        List<Step> steps = stepParser.parse(binding);
        DeploymentResourceYamlRequest request =
                new DeploymentResourceYamlRequest();
        final String title = "twitter-search-source-binding";
        request.setName(title);
        request.setSteps(steps.toArray(new Step[0]));

        final var emptyListJSON = "[]";
        given()
                .when()
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .get()
                .then()
                .statusCode(Response.Status.OK.getStatusCode())
                .body(is(emptyListJSON));

        given()
                .when()
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .post("customResource")
                .then()
                .statusCode(Response.Status.OK.getStatusCode());

        given()
                .when()
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .get()
                .then()
                .statusCode(Response.Status.OK.getStatusCode())
                .body(is(emptyListJSON));


        given()
                .when()
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .post()
                .then()
                .statusCode(Response.Status.OK.getStatusCode());

        given()
                .when()
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .delete(title)
                .then()
                .statusCode(Response.Status.OK.getStatusCode());

        given()
                .when()
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .get()
                .then()
                .statusCode(Response.Status.OK.getStatusCode())
                .body(is(emptyListJSON));
    }

    @Test
    void testExceptions() {
        given()
                .when()
                .contentType(MediaType.APPLICATION_JSON)
                .post("customResource")
                .then()
                .statusCode(Response.Status.BAD_REQUEST.getStatusCode());
    }
}
