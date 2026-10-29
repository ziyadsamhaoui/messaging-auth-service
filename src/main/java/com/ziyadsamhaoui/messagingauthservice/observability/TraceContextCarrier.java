package com.ziyadsamhaoui.messagingauthservice.observability;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapSetter;

import java.util.HashMap;
import java.util.Map;

public final class TraceContextCarrier {

    public static final String TRACEPARENT = "traceparent";

    private static final W3CTraceContextPropagator PROPAGATOR = W3CTraceContextPropagator.getInstance();

    private static final TextMapSetter<Map<String, String>> SETTER = Map::put;

    private static final TextMapGetter<Map<String, String>> GETTER = new TextMapGetter<>() {
        @Override
        public Iterable<String> keys(Map<String, String> carrier) {
            return carrier.keySet();
        }

        @Override
        public String get(Map<String, String> carrier, String key) {
            return carrier == null ? null : carrier.get(key);
        }
    };

    private TraceContextCarrier() {
    }

    public static String currentTraceParent() {
        return traceParent(Context.current());
    }

    static String traceParent(Context context) {
        if (context == null || !Span.fromContext(context).getSpanContext().isValid()) {
            return null;
        }
        Map<String, String> carrier = new HashMap<>(2);
        PROPAGATOR.inject(context, carrier, SETTER);
        return carrier.get(TRACEPARENT);
    }

    public static Context extract(String traceParent) {
        if (traceParent == null || traceParent.isBlank()) {
            return null;
        }
        Map<String, String> carrier = Map.of(TRACEPARENT, traceParent.trim());
        Context extracted = PROPAGATOR.extract(Context.root(), carrier, GETTER);
        return Span.fromContext(extracted).getSpanContext().isValid() ? extracted : null;
    }
}
