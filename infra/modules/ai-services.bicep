@description('Name of the AI Services account')
param name string

@description('Location for the resource')
param location string

@description('Tags for the resource')
param tags object = {}

@description('Model to deploy')
param modelName string

@description('Model format')
param modelFormat string

@description('Model version')
param modelVersion string

resource aiServices 'Microsoft.CognitiveServices/accounts@2024-10-01' = {
  name: name
  location: location
  tags: tags
  kind: 'AIServices'
  sku: {
    name: 'S0'
  }
  properties: {
    customSubDomainName: name
    publicNetworkAccess: 'Enabled'
    disableLocalAuth: true // Force Entra ID auth only
  }
}

// Lenient content filter policy (all thresholds at High)
resource lenientFilter 'Microsoft.CognitiveServices/accounts/raiPolicies@2024-10-01' = {
  parent: aiServices
  name: 'lenient-filter'
  properties: {
    basePolicyName: 'Microsoft.DefaultV2'
    mode: 'Blocking'
    contentFilters: [
      { name: 'hate', blocking: true, enabled: true, source: 'Prompt', severityThreshold: 'High' }
      { name: 'sexual', blocking: true, enabled: true, source: 'Prompt', severityThreshold: 'High' }
      { name: 'selfharm', blocking: true, enabled: true, source: 'Prompt', severityThreshold: 'High' }
      { name: 'violence', blocking: true, enabled: true, source: 'Prompt', severityThreshold: 'High' }
      { name: 'hate', blocking: true, enabled: true, source: 'Completion', severityThreshold: 'High' }
      { name: 'sexual', blocking: true, enabled: true, source: 'Completion', severityThreshold: 'High' }
      { name: 'selfharm', blocking: true, enabled: true, source: 'Completion', severityThreshold: 'High' }
      { name: 'violence', blocking: true, enabled: true, source: 'Completion', severityThreshold: 'High' }
    ]
  }
}

// Deploy the model with lenient filter
resource modelDeployment 'Microsoft.CognitiveServices/accounts/deployments@2024-10-01' = {
  parent: aiServices
  name: modelName
  dependsOn: [lenientFilter]
  sku: {
    name: 'GlobalStandard'
    capacity: 100
  }
  properties: {
    model: {
      format: modelFormat
      name: modelName
      version: modelVersion
    }
    raiPolicyName: 'lenient-filter'
  }
}

output endpoint string = 'https://${name}.openai.azure.com/'
output name string = aiServices.name
output id string = aiServices.id
