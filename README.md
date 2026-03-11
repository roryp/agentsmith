# Agent Smith - Matrix Combat Arena

<img src="./docs/gameplay.png" alt="Agent Smith Gameplay" width="800"/>

A multi-agent Matrix combat demo built with **Spring Boot 4**, **LangChain4j Agentic**, and **D3.js**.

Agent Smith (supervisor) coordinates Agents Brown and Jones to fight Neo in pixel-art combat rounds — powered by GPT-5-mini on Azure AI Services via the LangChain4j `supervisorBuilder` pattern.

![Matrix Combat Arena](docs/screenshot.png)

## Architecture

```
┌─────────────────────────────────────────────────┐
│  D3.js Frontend (pixel characters + Matrix rain) │
│  SSE streaming ← /api/fight                      │
└──────────────────────┬──────────────────────────┘
                       │
┌──────────────────────▼──────────────────────────┐
│  Spring Boot 4 (CombatController + SSE)          │
└──────────────────────┬──────────────────────────┘
                       │
┌──────────────────────▼──────────────────────────┐
│  LangChain4j Agentic Supervisor Pattern          │
│                                                  │
│  Smith (SupervisorAgent)                         │
│    ├── Brown (@Agent sub-agent)                  │
│    └── Jones (@Agent sub-agent)                  │
└──────────────────────┬──────────────────────────┘
                       │ DefaultAzureCredential
┌──────────────────────▼──────────────────────────┐
│  Azure AI Services (GPT-5-mini)                  │
│  Managed Identity / Entra ID auth                │
└─────────────────────────────────────────────────┘
```

## Prerequisites

- **Java 21+**
- **Maven 3.9+**
- **Azure CLI** (`az login`)
- **Azure Developer CLI** (`azd auth login`)
- An Azure subscription

## Quick Start

### 1. Deploy the AI model to Azure

```bash
azd auth login
azd up
```

This provisions:
- Azure AI Services account with GPT-5-mini deployment (100K TPM)
- Lenient content filter (all thresholds set to High)
- Your signed-in user gets `Cognitive Services OpenAI User` role

### 2. Grant yourself access (if not already done)

```bash
USER_ID=$(az ad signed-in-user show --query id -o tsv)
az role assignment create \
  --role "Cognitive Services OpenAI User" \
  --assignee $USER_ID \
  --scope $(az cognitiveservices account show -n <ai-account-name> -g <rg-name> --query id -o tsv)
```

### 3. Run locally

```bash
# Get the endpoint from azd
azd env get-values | grep AZURE_AI

# Set env vars and run
export AZURE_AI_ENDPOINT="https://<your-account>.openai.azure.com/"
export AZURE_AI_DEPLOYMENT="gpt-5-mini"
mvn spring-boot:run
```

Open **http://localhost:8080** in your browser.

## How to Play

1. Click **⚔ FIGHT ⚔** to start a combat round
2. Agent Smith (supervisor) deploys Brown and Jones against Neo
3. Each fight has a **~30% chance the agent wins** — Neo can lose!
4. Watch the combat log for Matrix-themed fight narratives
5. Click **↺ RESET** to zero the scores
6. Keep fighting to see who comes out on top

## Scoring

| Event | Neo | Agents |
|-------|-----|--------|
| Agent loses fight | +1 | — |
| Agent wins fight | — | +1 |

## Tech Stack

| Component | Technology |
|-----------|-----------|
| Backend | Spring Boot 4.0.3, Java 21 |
| AI Agents | LangChain4j 1.12.1 + langchain4j-agentic 1.12.1-beta21 |
| Agent Pattern | `AgenticServices.supervisorBuilder()` with `@Agent` sub-agents |
| LLM | GPT-5-mini on Azure AI Services |
| Auth | `DefaultAzureCredential` (Managed Identity / Entra ID) |
| Frontend | D3.js v7, pixel art, SSE streaming |
| Infra | Bicep + Azure Developer CLI (`azd`) |

## Project Structure

```
src/main/java/com/agentsmith/
├── AgentSmithApplication.java          # Spring Boot entry point
├── agents/
│   ├── AgentBrown.java                 # @Agent - Brown sub-agent
│   ├── AgentJones.java                 # @Agent - Jones sub-agent
│   └── MatrixSupervisor.java           # Supervisor interface (Smith)
├── config/
│   └── AiConfig.java                   # ChatModel bean (Azure + DefaultAzureCredential)
├── controller/
│   └── CombatController.java           # SSE /api/fight endpoint
└── service/
    ├── CombatEvent.java                # SSE event record
    └── CombatService.java              # Supervisor orchestration + scoring

src/main/resources/
├── application.properties
└── static/index.html                   # D3.js pixel combat frontend

infra/
├── main.bicep                          # Azure infra (AI Services + model)
├── main.parameters.json
└── modules/
    └── ai-services.bicep               # AI account + GPT-5-mini deployment + content filter
```

## Cleanup

```bash
azd down --force --purge
```
