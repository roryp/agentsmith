targetScope = 'subscription'

@minLength(1)
@maxLength(64)
@description('Name of the environment')
param environmentName string

@minLength(1)
@description('Primary location for all resources')
param location string

@description('The deployment name exposed to applications')
param deploymentName string = 'gpt-4o-mini-fast'

@description('The base model to deploy')
param modelName string = 'gpt-4o-mini'

@description('The model format')
param modelFormat string = 'OpenAI'

@description('The model version')
param modelVersion string = '2024-07-18'

@description('The model deployment capacity')
param modelCapacity int = 500

@description('Minimum number of Container App replicas')
param minReplicas int = 2

@description('Maximum number of Container App replicas')
param maxReplicas int = 10

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
    deploymentName: deploymentName
    modelName: modelName
    modelFormat: modelFormat
    modelVersion: modelVersion
    modelCapacity: modelCapacity
  }
}

// Azure Container Registry
module acr 'modules/acr.bicep' = {
  name: 'acr'
  scope: rg
  params: {
    name: '${abbrs.containerRegistry}${resourceToken}'
    location: location
    tags: tags
  }
}

// Container App
module containerApp 'modules/container-app.bicep' = {
  name: 'container-app'
  scope: rg
  params: {
    name: '${abbrs.containerApp}${resourceToken}'
    location: location
    tags: tags
    aiServicesEndpoint: aiServices.outputs.endpoint
    aiServicesName: aiServices.outputs.name
    deploymentName: deploymentName
    imageName: 'mcr.microsoft.com/azuredocs/containerapps-helloworld:latest'
    registryServer: acr.outputs.loginServer
    registryUsername: acr.outputs.name
    registryPassword: acr.outputs.password
    minReplicas: minReplicas
    maxReplicas: maxReplicas
  }
}

output AZURE_AI_ENDPOINT string = aiServices.outputs.endpoint
output AZURE_AI_DEPLOYMENT string = deploymentName
output AZURE_CONTAINER_REGISTRY_ENDPOINT string = acr.outputs.loginServer
output AZURE_CONTAINER_REGISTRY_NAME string = acr.outputs.name
output WEB_URI string = containerApp.outputs.uri
