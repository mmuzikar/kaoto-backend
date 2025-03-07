package io.kaoto.backend.metadata.parser.step.camelroute;

import io.kaoto.backend.metadata.ParseCatalog;
import io.kaoto.backend.metadata.catalog.InMemoryCatalog;
import io.kaoto.backend.model.parameter.BooleanParameter;
import io.kaoto.backend.model.parameter.NumberParameter;
import io.kaoto.backend.model.parameter.ObjectParameter;
import io.kaoto.backend.model.parameter.Parameter;
import io.kaoto.backend.model.parameter.StringParameter;
import io.kaoto.backend.model.step.Step;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import javax.inject.Inject;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class CamelRouteParseCatalogTest {

    public static final String CAMEL_ZIP = "resource://camel-3.19.0.zip";

    @Inject
    public void setParseCatalog(final CamelRouteParseCatalog parseCatalog) {
        this.parseCatalog = parseCatalog;
    }

    private CamelRouteParseCatalog parseCatalog;

    @Test
    void shouldLoadFromJar() {
        InMemoryCatalog<Step> catalog = new InMemoryCatalog<>();

        ParseCatalog<Step> camelParser = parseCatalog.getParser(CAMEL_ZIP);
        List<Step> steps = camelParser.parse().join();

        assertTrue(catalog.store(steps));

        Step browseComponentSink = catalog.searchByID("browse-producer");
        Step browseComponentSource = catalog.searchByID("browse-consumer");
        Step browseComponentAction = catalog.searchByID("browse-action");

        assertBrowseJsonHasBeenParsedCorrectly(browseComponentSource, Step.START, false);
        assertBrowseJsonHasBeenParsedCorrectly(browseComponentSink, Step.END, false);
        assertBrowseJsonHasBeenParsedCorrectly(browseComponentAction, Step.MIDDLE, false);
    }

    @Test
    void shouldLoadFromLocalFolder() throws URISyntaxException {
        Path camelJsonRoute = Path.of(
                CamelRouteFileProcessorTest.class.getResource(".").toURI());

        ParseCatalog<Step> camelParser = parseCatalog.getLocalFolder(camelJsonRoute);
        List<Step> steps = camelParser.parse().join().stream().filter(Objects::nonNull).collect(Collectors.toList());

        assertEquals(3, steps.size());

        BiFunction<List<Step>, String, Step> fetchBrowse =
                (stepList, stepType) -> stepList.stream()
                        .filter(step -> stepType.equals(step.getType())).findFirst().get();

        Step browseComponentSink = fetchBrowse.apply(steps, Step.END);
        Step browseComponentSource = fetchBrowse.apply(steps, Step.START);
        Step browseComponentAction = fetchBrowse.apply(steps, Step.MIDDLE);

        assertBrowseJsonHasBeenParsedCorrectly(browseComponentSink, Step.END, false);
        assertBrowseJsonHasBeenParsedCorrectly(browseComponentSource, Step.START, false);
        assertBrowseJsonHasBeenParsedCorrectly(browseComponentAction, Step.MIDDLE, false);
    }

    @Test
    void loadFromLocalZip() {
        InMemoryCatalog<Step> catalog = new InMemoryCatalog<>();

        ParseCatalog<Step> camelParser = parseCatalog.getParser(CAMEL_ZIP);
        List<Step> steps = camelParser.parse().join();

        assertTrue(catalog.store(steps));

        var salesforces = catalog.searchByName("salesforce");
        assertNotNull(salesforces);
        assertEquals(3, salesforces.size());
    }

    private void assertBrowseJsonHasBeenParsedCorrectly(
            final Step parsedStep, final String type,
            final boolean assertDescription) {
        Map<String, String> typeToIdConversion = Map.of(
                Step.MIDDLE, "action",
                Step.END, "producer",
                Step.START, "consumer"
        );

        String expectedType = typeToIdConversion.get(type);

        assertEquals("browse-" + expectedType, parsedStep.getId());
        assertEquals("browse", parsedStep.getName());
        assertEquals("Browse", parsedStep.getTitle());
        assertEquals("Inspect the messages received on endpoints supporting BrowsableEndpoint.",
                parsedStep.getDescription());
        assertEquals(type, parsedStep.getType());
        assertIterableEquals(List.of("name"), parsedStep.getRequired());

        Map<String, Parameter> expectedParameterValues = Map.of(
                "name", new StringParameter("name", "Name", "d1", null, null),
                "bridgeErrorHandler", new BooleanParameter("bridgeErrorHandler", "Bridge Error Handler", "d2", false),
                "exceptionHandler", new ObjectParameter("exceptionHandler", "Exception Handler", "d3", null),
                "exchangePattern", new ObjectParameter("exchangePattern", "Exchange Pattern", "d4", null),
                "lazyStartProducer", new BooleanParameter("lazyStartProducer", "Lazy Start Producer", "d5", false),
                "step-id-kaoto", new StringParameter("step-id-kaoto", "Step ID", "Identifier of this step inside the " +
                        "route.", null, null)
        );

        expectedParameterValues.get("name").setPath(true);

        parsedStep.getParameters().forEach(
                parameter -> {
                    assertEquals(parameter.getId(), expectedParameterValues.get(parameter.getId()).getId());
                    assertEquals(parameter.isPath(), expectedParameterValues.get(parameter.getId()).isPath());
                    assertEquals(parameter.getTitle(), expectedParameterValues.get(parameter.getId()).getTitle());
                    assertEquals(parameter.getDefaultValue(),
                            expectedParameterValues.get(parameter.getId()).getDefaultValue());
                    if (assertDescription) {
                        assertEquals(parameter.getDescription(),
                                expectedParameterValues.get(parameter.getId()).getDescription());
                    }
                }
        );
    }

    @Test
    void checkTypesOfAttributesInCamelComponents() {
        InMemoryCatalog<Step> catalog = new InMemoryCatalog<>();
        ParseCatalog<Step> camelParser = parseCatalog.getParser(CAMEL_ZIP);
        assertTrue(catalog.store(camelParser.parse().join()));
        Step consumer = catalog.searchByID("timer-consumer");
        assertTrue(consumer.getParameters().stream().parallel()
                .anyMatch(p -> p.getId().equalsIgnoreCase("delay") && p instanceof StringParameter));
        Step activemq = catalog.searchByID("activemq-action");
        assertTrue(activemq.getParameters().stream().parallel()
                .anyMatch(p -> p.getId().equalsIgnoreCase("deliveryDelay") && p instanceof NumberParameter));
    }


    @Test
//    @Timeout(value = 300, unit = TimeUnit.MILLISECONDS)
    void testSpeed() {
        InMemoryCatalog<Step> catalog = new InMemoryCatalog<>();
        ParseCatalog<Step> camelParser = parseCatalog.getParser(CAMEL_ZIP);
        List<Step> steps = camelParser.parse().join();
        assertTrue(catalog.store(steps));
    }
}
