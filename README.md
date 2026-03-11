# Agent Smith - Matrix Combat Arena

A multi-agent Matrix combat demo built with **Spring Boot 4**, **LangChain4j Agentic**, and **D3.js**.

Agent Smith (supervisor) coordinates Agents Brown and Jones to fight Neo in pixel-art combat rounds — powered by GPT-5-nano on Azure AI Services using three LangChain4j agentic patterns.

## Architecture

```
┌─────────────────────────────────────────────────┐
│  D3.js Frontend (pixel characters + Matrix rain) │
│  SSE streaming ← /api/fight/{mode}               │
└──────────────────────┬──────────────────────────┘
                       │
┌──────────────────────▼──────────────────────────┐
│  Spring Boot 4 (CombatController + SSE)          │
└──────────────────────┬──────────────────────────┘
                       │
┌──────────────────────▼──────────────────────────┐
│  LangChain4j Agentic Patterns                    │
│                                                  │
│  ⚔ Supervisor: Smith plans, deploys Brown+Jones  │
│  ⚡ Parallel:  Brown+Jones fight simultaneously  │
│  🔄 Loop:     Auto-battle until first to 5 wins  │
│                                                  │
│  Smith (SupervisorAgent)                         │
│    ├── Brown (@Agent sub-agent)                  │
│    └── Jones (@Agent sub-agent)                  │
└──────────────────────┬──────────────────────────┘
                       │ DefaultAzureCredential
┌──────────────────────▼──────────────────────────┐
│  Azure AI Services (GPT-5-nano)                  │
│  Managed Identity / Entra ID auth                │
└─────────────────────────────────────────────────┘
```

## Prerequisites

- **Java 21+**
- **Maven 3.9+**
- **Azure CLI** — [install](https://learn.microsoft.com/cli/azure/install-azure-cli)
- **Azure Developer CLI** — [install](https://learn.microsoft.com/azure/developer/azure-developer-cli/install-azd)
- An Azure subscription

## Quick Start

### 1. Login to Azure

```bash
az login
azd auth login
```

### 2. Deploy the AI model

```bash
azd up
```

This provisions:
- Azure AI Services account with **GPT-5-nano** deployment (100K TPM)
- Lenient content filter (all thresholds at High)

### 3. Grant yourself the OpenAI User role

**Bash/macOS:**
```bash
USER_ID=$(az ad signed-in-user show --query id -o tsv)
SCOPE=$(azd env get-values | grep AZURE_AI_ENDPOINT | cut -d'"' -f2)
ACCOUNT_NAME=$(echo $SCOPE | sed 's|https://||;s|\.openai\.azure\.com/||')
RG=$(az cognitiveservices account list --query "[?name=='$ACCOUNT_NAME'].resourceGroup" -o tsv)

az role assignment create \
  --role "Cognitive Services OpenAI User" \
  --assignee $USER_ID \
  --scope $(az cognitiveservices account show -n $ACCOUNT_NAME -g $RG --query id -o tsv)
```

**PowerShell:**
```powershell
$userId = az ad signed-in-user show --query id -o tsv
# Use the account name from azd env get-values output
az role assignment create `
  --role "Cognitive Services OpenAI User" `
  --assignee $userId `
  --scope (az cognitiveservices account show -n <ai-account-name> -g <rg-name> --query id -o tsv)
```

> **Note:** Role assignment can take 1-2 minutes to propagate.

### 4. Run the app locally

**Bash/macOS:**
```bash
export AZURE_AI_ENDPOINT=$(azd env get-values | grep AZURE_AI_ENDPOINT | cut -d'"' -f2)
export AZURE_AI_DEPLOYMENT="gpt-5-nano"
mvn spring-boot:run
```

**PowerShell:**
```powershell
$env:AZURE_AI_ENDPOINT = "https://<your-account>.openai.azure.com/"
$env:AZURE_AI_DEPLOYMENT = "gpt-5-nano"
mvn spring-boot:run
```

> **Tip:** Run `azd env get-values` to see your endpoint.

### 5. Open the game

Open **http://localhost:8080** in your browser.

## How to Play

| Button | Pattern | What happens |
|--------|---------|-------------|
| **⚔ FIGHT** | Supervisor (#5) | Smith coordinates — plans, deploys Brown then Jones via LLM |
| **⚡ FAST** | Parallel (#2) | Brown and Jones fight Neo simultaneously — faster |
| **🔄 AUTO 5** | Loop (#3) | Auto-battles rounds until someone hits 5 wins |
| **↺ RESET** | — | Resets scores to 0 |

- Each fight has a **~30% chance the agent wins** — Neo can lose!
- Agent Smith delivers a random movie quote each round
- Watch the combat log for fight results and Smith's commentary

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
| Agent Patterns | Supervisor, Parallel, Loop (`AgenticServices`) |
| LLM | GPT-5-nano on Azure AI Services |
| Auth | `DefaultAzureCredential` (Managed Identity / Entra ID) |
| Frontend | D3.js v7, pixel art, SSE streaming |
| Infra | Bicep + Azure Developer CLI (`azd`) |

## Project Structure

```
src/main/java/com/agentsmith/
├── AgentSmithApplication.java          # Spring Boot entry point
├── agents/
│   ├── AgentBrown.java                 # @Agent sub-agent (Brown)
│   ├── AgentJones.java                 # @Agent sub-agent (Jones)
│   └── MatrixSupervisor.java           # Supervisor interface (Smith)
├── config/
│   └── AiConfig.java                   # ChatModel bean (Azure + DefaultAzureCredential)
├── controller/
│   └── CombatController.java           # SSE endpoints: /api/fight/{mode}
└── service/
    ├── CombatEvent.java                # SSE event record
    └── CombatService.java              # 3 patterns: supervisor, parallel, auto-battle

src/main/resources/
├── application.properties
└── static/index.html                   # D3.js pixel combat frontend

infra/
├── main.bicep                          # AI Services + model deployment
├── main.parameters.json
└── modules/
    └── ai-services.bicep               # AI account + GPT-5-nano + content filter
```

## Cleanup

```bash
azd down --force --purge
```
