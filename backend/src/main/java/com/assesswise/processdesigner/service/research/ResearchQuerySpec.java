package com.assesswise.processdesigner.service.research;

import com.assesswise.processdesigner.domain.QueryIntent;
import com.assesswise.processdesigner.domain.QueryOrigin;

/**
 * One search this run intends to make, and why.
 *
 * <p>The intent is not decoration. It decides which connectors are worth asking — a REGULATION
 * query has no business going to Hacker News, and a BENCHMARK query is wasted on Wikipedia — and it
 * lets the run report which angles of the problem were actually researched.
 */
public record ResearchQuerySpec(String text, QueryIntent intent, QueryOrigin origin) {

    public static ResearchQuerySpec model(String text, QueryIntent intent) {
        return new ResearchQuerySpec(text, intent, QueryOrigin.MODEL);
    }

    public static ResearchQuerySpec template(String text, QueryIntent intent) {
        return new ResearchQuerySpec(text, intent, QueryOrigin.TEMPLATE);
    }
}
