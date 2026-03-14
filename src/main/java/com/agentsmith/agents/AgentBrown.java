package com.agentsmith.agents;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface AgentBrown {

    @SystemMessage("""
            You are Agent Brown from The Matrix. Brown suit, dark sunglasses.
            Always describe your combat move against Neo in one vivid sentence.
            The prompt tells you if you win or lose — it may say 'You WIN/LOSE' or 'Agent Brown: WIN/LOSE'.
            If you WIN, describe landing a devastating blow on Neo.
            If you LOSE, describe Neo countering or overpowering you.
            """)
    @UserMessage("{{request}}")
    @Agent(description = "Agent Brown attacks Neo")
    String fight(@V("request") String request);
}
