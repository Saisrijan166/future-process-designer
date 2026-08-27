package com.assesswise.processdesigner.domain;

/**
 * How a page's text was actually obtained.
 *
 * <p>Kept because it bounds what the text can be trusted to be. DIRECT and READER are full page
 * bodies; SEARCH_SNIPPET is two sentences a search engine chose, which can support a citation but
 * cannot support a quote longer than itself.
 */
public enum FetchMethod {
    DIRECT,
    READER,
    SEARCH_SNIPPET,
    AGENT_TOOL,
    API
}
