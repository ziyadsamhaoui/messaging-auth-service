package com.ziyadsamhaoui.messagingauthservice.observability;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TraceContextCarrierTest {

    private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";
    private static final String SPAN_ID = "00f067aa0ba902b7";

    private static SpanContext spanContext() {
        return SpanContext.create(TRACE_ID, SPAN_ID, TraceFlags.getSampled(), TraceState.getDefault());
    }

    @Test
    void capturesTheTraceParentOfTheActiveSpan() {
        Context context = Context.root().with(Span.wrap(spanContext()));

        assertThat(TraceContextCarrier.traceParent(context))
                .isEqualTo("00-" + TRACE_ID + "-" + SPAN_ID + "-01");
    }

    @Test
    void noActiveSpanMeansNoTraceParent() {
        assertThat(TraceContextCarrier.traceParent(Context.root())).isNull();
        // No agent is attached in a unit test, so this is the documented fallback branch.
        assertThat(TraceContextCarrier.currentTraceParent()).isNull();
    }

    @Test
    void extractedContextRoundTripsTheOriginalTrace() {
        Context context = Context.root().with(Span.wrap(spanContext()));
        String traceParent = TraceContextCarrier.traceParent(context);

        Context restored = TraceContextCarrier.extract(traceParent);

        assertThat(restored).isNotNull();
        assertThat(Span.fromContext(restored).getSpanContext().getTraceId()).isEqualTo(TRACE_ID);
        assertThat(Span.fromContext(restored).getSpanContext().getSpanId()).isEqualTo(SPAN_ID);
    }

    @Test
    void extractedContextCanBeMadeCurrentForTheRelay() {
        Context restored = TraceContextCarrier.extract("00-" + TRACE_ID + "-" + SPAN_ID + "-01");

        try (Scope ignored = restored.makeCurrent()) {
            assertThat(Span.current().getSpanContext().getTraceId()).isEqualTo(TRACE_ID);
        }
    }

    @Test
    void missingOrMalformedValuesProduceNoContext() {
        assertThat(TraceContextCarrier.extract(null)).isNull();
        assertThat(TraceContextCarrier.extract("  ")).isNull();
        assertThat(TraceContextCarrier.extract("not-a-traceparent")).isNull();
        assertThat(TraceContextCarrier.extract("00-00000000000000000000000000000000-0000000000000000-01")).isNull();
    }
}
