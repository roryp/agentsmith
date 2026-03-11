@description('Name of the container registry')
param name string

@description('Location')
param location string

@description('Tags')
param tags object = {}

resource acr 'Microsoft.ContainerRegistry/registries@2023-07-01' = {
  name: name
  location: location
  tags: tags
  sku: { name: 'Basic' }
  properties: {
    adminUserEnabled: true
  }
}

output loginServer string = acr.properties.loginServer
output name string = acr.name
output id string = acr.id
output password string = acr.listCredentials().passwords[0].value
