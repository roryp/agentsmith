targetScope = 'subscription'

@minLength(1)
@maxLength(64)
@description('Name of the environment')
param environmentName string

@minLength(1)
@description('Primary location for all resources')
param location string

@description('The model to deploy')
param modelName string = 'gpt-5-nano'

@description('The model format')
param modelFormat string = 'OpenAI'

@description('The model version')
param modelVersion string = '2025-08-07'

var abbrs = loadJsonContent('abbreviations.json')
var resourceToken = toLower(uniqueString(subscription().id, environmentName, location))
var tags = { 'azd-env-name': environmentName }

// Resource Group
resource rg 'Microsoft.Resources/resourceGroups@2024-03-01' = {
  name: '${abbrs.resourceGroup}${environmentName}'
  location: location
  tags: tags
}

// Azure AI Services account (hosts model via Foundry Models)
module aiServices 'modules/ai-services.bicep' = {
  name: 'ai-services'
  scope: rg
  params: {
    name: '${abbrs.aiServices}${resourceToken}'
    location: location
    tags: tags
    modelName: modelName
    modelFormat: modelFormat
    modelVersion: modelVersion
  }
}

output AZURE_AI_ENDPOINT string = aiServices.outputs.endpoint
output AZURE_AI_DEPLOYMENT string = modelName
