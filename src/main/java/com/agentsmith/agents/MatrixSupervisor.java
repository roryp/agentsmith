package com.agentsmith.agents;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.V;

/**
 * Typed supervisor interface for Agent Smith.
 * The supervisor coordinates Brown and Jones to fight Neo.
 */
public interface MatrixSupervisor {

    @Agent
    String invoke(@V("request") String request);
}
