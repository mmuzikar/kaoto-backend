package io.zimara.backend.api.service.viewdefinitions;

import io.zimara.backend.api.service.parser.KameletBindingParserService;
import io.zimara.backend.api.service.parser.ParserService;
import io.zimara.backend.model.view.View;
import io.zimara.backend.model.view.IntegrationView;
import org.jboss.logging.Logger;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.QueryParam;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class ViewDefinitionService {

    private List<ParserService> parsers = new ArrayList<>();
    private Logger log = Logger.getLogger(ViewDefinitionService.class);

    @Inject
    public void setKameletBindingParserService(KameletBindingParserService kameletBindingParserService) {
        parsers.add(kameletBindingParserService);
    }

    public List<View> views(@QueryParam("yaml") String yaml) {
        List<View> views = new ArrayList<>();
        for (var parser : parsers) {
            log.trace("Using " + parser.getIdentifier());
            if (parser.appliesTo(yaml)) {
                log.trace("Applying " + parser.getIdentifier());
                views.add(new IntegrationView(parser.parse(yaml), parser.getIdentifier()));
            }
        }
        return views;
    }
}
