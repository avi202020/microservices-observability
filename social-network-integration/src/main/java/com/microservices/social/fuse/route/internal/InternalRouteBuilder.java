package com.microservices.social.fuse.route.internal;

import com.microservices.social.fuse.configuration.StravaConfiguration;
import com.microservices.social.fuse.processor.ExceptionProcessor;
import com.microservices.social.fuse.processor.LogHeaderProcessor;
import com.microservices.social.fuse.route.RouteDescriptor;
import io.opentracing.Span;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.apache.camel.opentracing.ActiveSpanManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

@Component("InternalRouteBuilder")
public class InternalRouteBuilder extends RouteBuilder {

    static final Logger logger = LoggerFactory.getLogger(InternalRouteBuilder.class);

    @Autowired
    private StravaConfiguration stravaConfig;

    @Autowired
    private ExceptionProcessor exceptionProcessor;

    @Autowired
    private LogHeaderProcessor logHeaderProcessor;

    @Override
    public void configure() throws Exception {

        onException(Exception.class)
            .handled(true)
            .process(exceptionProcessor)
            .redeliveryDelay(150)
            .maximumRedeliveries(2) // 1 + 2 retries
            .to("log:exception");

        // /--------------------------------------------------\
        // | Internal route definition                        |
        // \--------------------------------------------------/

        from(RouteDescriptor.INTERNAL_POST_STRAVA.getUri())
            .log(LoggingLevel.WARN, logger, "internal route: preparing to call external api using http4 producer")
            .marshal().json(JsonLibrary.Jackson)
            .pipeline()
                .setHeader(Exchange.HTTP_METHOD, constant(HttpMethod.POST))
                .setHeader(Exchange.CONTENT_TYPE, constant(MediaType.APPLICATION_JSON))
                .setHeader(stravaConfig.getApiKeyName(), header(stravaConfig.getApiKeyName()))
                //.delay(simple("${random(1000,2000)}"))
                .process(logHeaderProcessor)
                .removeHeader(Exchange.HTTP_PATH)
                .log("http4://"+stravaConfig.getHost()+":"+stravaConfig.getPort()+stravaConfig.getContextPath()+"?connectTimeout=500&bridgeEndpoint=true")
                .bean("InternalRouteBuilder", "addTracer")
                .to("http4://"+stravaConfig.getHost()+":"+stravaConfig.getPort()+stravaConfig.getContextPath()+"?connectTimeout=500&bridgeEndpoint=true")
            .end()
            .unmarshal().json(JsonLibrary.Jackson)
            .end();

    }

    public void addTracer(Exchange exchange){
        String userAgent = (String) exchange.getIn().getHeader("user-agent");
        Span span = ActiveSpanManager.getSpan(exchange);
        span.setBaggageItem("user-agent", userAgent);
    }

}
