import * as AWS from "aws-sdk"

const route53domains = new AWS.Route53Domains({
  region: "us-east-1",
  apiVersion: "2014-05-15",
})

async function main() {
  const env = requireEnv("ENV")
  if (env !== "dev") {
    console.log("Not registering domain for other environments than dev")
    return
  }

  const domainName = `${env}-tiuha.com`
  console.log(`Ensuring domain name ${domainName} is registered`)

  const domains = await listDomains()
  console.log("Found domains:", domains)

  const existing = domains.find(_ => _.DomainName === domainName)
  if (existing) {
    console.log(`All good, domain ${domainName} is already registered:`, existing)
  } else {
    await registerDomain(domainName, `fmi-tiuha-${env}@fmi.fi`)
  }
}

async function listDomains(marker?: string): Promise<AWS.Route53Domains.ListDomainsResponse["Domains"]> {
  const response = await route53domains.listDomains({ Marker: marker }).promise()
  if (!response.NextPageMarker) {
    return response.Domains
  } else {
    const nextDomains = await listDomains(response.NextPageMarker)
    return response.Domains.concat(nextDomains)
  }
}

async function registerDomain(domainName: string, email: string) {
  console.log(`Registering domain ${domainName}`)
  const contactDetail: AWS.Route53Domains.ContactDetail = {
    ContactType: "PERSON",
    FirstName: "Henry",
    LastName: "Heikkinen",
    Email: email,
    PhoneNumber: "+358.400467708",
    AddressLine1: "Yliopistonkatu 4",
    CountryCode: "FI",
    City: "Helsinki",
    ZipCode: "00100",
  }

  const registration = await route53domains.registerDomain({
    DomainName: domainName,
    AutoRenew: true,
    DurationInYears: 1,
    AdminContact: contactDetail,
    RegistrantContact: contactDetail,
    TechContact: contactDetail,
  }).promise()

  while (true) {
    const detail = await route53domains.getOperationDetail({ OperationId: registration.OperationId }).promise()
    if (detail.Status === "SUCCESSFUL") {
      console.log("Domain registration successful")
      break
    }
    if (detail.Status === "FAILED") {
      throw Error(`Domain registration entered ${detail.Status} status`)
    }
    await delay(10_000)
  }
}

async function delay(ms: number): Promise<void> {
  return new Promise<void>(resolve => setTimeout(resolve, ms))
}

function requireEnv(key: string): string {
  const value = process.env[key]
  if (typeof value === "undefined")
    throw Error(`Environment variable ${key} is required`)
  return value
}

main().catch(err => {
  console.log(err)
  process.exit(1)
})
