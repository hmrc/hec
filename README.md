# hec

More information about the HEC (Hidden Economy Conditionality) project can be found [here](https://www.gov.uk/government/publications/new-tax-checks-on-licence-renewal-applications).

This microservice serves the following purposes:
- stores tax check data associated with successful tax checks
- matches tax check data
- gets information from HMRC back end systems
- periodically sends tax check data in a file transfer process 


## Running the service
When running locally, the dependant services can be run using the service manager command
```
sm2 --start HEC_DEP
```
All HEC services can run via
```
sm2 --start HEC_ALL
```

To stop the frontend microservice from running on service manager (e.g. to run your own version locally), you can run:

```
sm2 --stop HEC
```

By default, this service runs on port `10105`. Test-only endpoint can be accessed by running with the option:
```
sbt run -Dplay.http.router=testOnlyDoNotUseInAppConf.Routes
```

## Running tests

You can run tests in Intellij by running:

```
sbt test
```

This service uses sbt-scoverage to provide test coverage reports.
Use the following command to run the tests with coverage and generate a report:

```
sbt clean coverage test coverageReport
```

## Endpoints

### `POST /hec/tax-check`
Generate a tax check code and save the tax check data. A `201` (CREATED) response is 
returned if the tax check has successfully been saved. Requires an active GG or Stride session.

Example request body:
```json
{
   "applicantDetails": {
      "ggCredId": "credId",
      "name": { 
         "firstName": "First",
         "lastName": "Last"
      },
      "dateOfBirth":"1922-12-01"
   },
   "licenceDetails":{
      "licenceType": "DriverOfTaxisAndPrivateHires",
      "licenceTimeTrading": "ZeroToTwoYears",
      "licenceValidityPeriod": "UpToThreeYears"
   },
   "taxDetails": { 
      "nino": "AB123456C",
      "sautr": "1234567890",
      "taxSituation":"SA",
      "saIncomeDeclared": "Yes",
      "saStatusResponse": {
         "sautr": "1234567890",
         "taxYear": 2020,
         "status": "ReturnFound"
      },
      "relevantIncomeTaxYear": 2020,
      "correctiveAction": "DormantAccountReactivated"
   },
   "taxCheckStartDateTime": "2021-12-02T14:56:11.75Z[Europe/London]",
   "source":"Stride",
   "languagePreference": "Welsh",
   "didConfirmUncertainEntityType": true,
   "filterFromFileTransfer": false,
   "type":"Individual"
}
```
Example response body:
```json
{
   "taxCheckData" : {
      "applicantDetails": {
         "ggCredId": "credId",
         "crn": "1234567890",
         "companyName": "Test Company"
      },
      "licenceDetails": {
         "licenceType": "OperatorOfPrivateHireVehicles",
         "licenceTimeTrading": "TwoToFourYears",
         "licenceValidityPeriod": "UpToOneYear"
      },
      "taxDetails": {
         "hmrcCTUTR" :"1111111111",
         "userSuppliedCTUTR": "1111111111",
         "ctIncomeDeclared": "No", 
         "ctStatus": {
            "ctutr":  "1234567890",
            "startDate":  "2020-10-01",
            "endDate":  "2019-30-09",
            "latestAccountingPeriod": {
               "startDate": "2020-01-31",
               "endDate": "2020-05-24",
               "ctStatus": "NoticeToFileIssued"
            }
         }, 
         "recentlyStaredTrading": "No",
         "chargeableForCT": "Yes",
         "correctiveAction": "Other"
      },
      "taxCheckStartDateTime": "2021-12-02T14:56:11.75Z[Europe/London]",
      "source": "Digital",
      "languagePreference": "English",
      "didConfirmUncertainEntityType": false,
      "filterFromFileTransfer": true,
      "type": "Company"
   },
   "taxCheckCode": "TLAR4D6HB",
   "expiresAfter": "2022-04-01",
   "createDate": "2021-12-02T14:56:49.618Z[Europe/London]",
   "isExtracted": false
}
```
N.B. the combinations of answers in the request and response bodies above do not correspond to actual
real business scenarios. A value for each field (even if optional) has been provided for illustrative 
purposes.


### `POST /hec/match-tax-check`
Check the details given in the request to see if they match an existing tax check in the database. A
`200` (OK) response is returned if a match result can be given. The example request body shows an example
for an individual and the example response body includes an example for a company. 

Example request body:
```json
{
   "taxCheckCode": "222RRR888",
   "licenceType": "ScrapMetalDealerSite",
   "verifier": {
      "dateofbirth": "1922-12-01"
   }
}
```

Example response body:
```json
{
   "matchRequest": {
      "taxCheckCode": "7PCR3ANY2",
      "licenceType": "ScrapMetalMobileCollector",
      "verifier": {
         "crn": "1234567890"
      }
   },
   "dateTimeChecked": "2021-12-02T15:25:57.728Z[Europe/London]",
   "status": "NoMatch"
}
```

### `GET /hec/unexpired-tax-checks`
Requires an active GG session. Gets summary data of all tax checks associated with the GG account associated 
with the GG session. Returns `200` (OK) if successful. If not tax checks are found, a `200` is still returned -
the response will just contain an empty array. 

Example response body:
```json
[
   {
      "licenceType": "ScrapMetalDealerSite", 
      "taxCheckCode": "PPL99KT6P",
      "expiresAfter": "2022-04-01",
      "createDate":"2021-12-02T15:35:20.638Z[Europe/London]"
   },
   {
      "licenceType": "ScrapMetalDealerSite",
      "taxCheckCode": "GALH79T79",
      "expiresAfter": "2022-04-01",
      "createDate":"2021-12-02T11:53:02.004Z[Europe/London]"
   }
]
```

### `GET /hec/sa-status/:utr/:taxYear`       
Get the self assessment (SA) return status for the given SA UTR and the tax year. The tax year
is the start year of the tax year, e.g. for the 2021/2022 tax year the input should be 2021. A 
`200` (OK) repsonse is given if the return status can be returned. Requires an active GG session.

Example response body:
```json
{
   "sautr": "1234567890",
   "taxYear": 2020,
   "status": "NoReturnFound"
}
```

### `GET /hec/ct-status/:utr/:startDate/:endDate`
Attempt to find the latest corporation tax (CT) accounting period within the given lookup period and the 
return status of that accounting period if one exists. A`200` (OK) repsonse is given if the return status can be 
returned. Requires an active GG session.

Example response body:
```json
{
   "ctStatus": {
      "ctutr":  "1234567890",
      "startDate":  "2020-10-01",
      "endDate":  "2019-30-09",
      "latestAccountingPeriod": {
         "startDate": "2020-01-31",
         "endDate": "2020-05-24",
         "ctStatus": "NoReturnFound"
      }
   }
}
```

### `GET /hec/ctutr/:crn`                          
Return the CT UTR associated with the given CRN (company registration number). Returns a `200` (OK) response
if a CT UTR can be found. If no CT UTR can be found a `404` (NOT FOUND) response is given. Requires an
active GG session.

Example response body:
```json
{
   "ctutr": "1234567890"
}
```

### `POST /hec/file-transfer/callback`
Handles a callback which is triggered from this service notifying downstream systems that a file is ready to 
pick up (c.f. [HEC Tax Check Data File Transfer](#hec-tax-check-data-file-transfer)). A `200` (OK)
response with no body will be returned if the callback has been handled successfully.

Example request body:
```json
{
   "notification": "FileProcessingFailure",
   "filename": "file.txt",
   "correlationID": "12345",
   "failureReason": "¯\\_(ツ)_//¯"
}
```

## Test-Only Endpoints
### `POST       /hec/test-only/tax-check`              
Unauthenticated test-only endpoint to save a tax check. A `201` (CREATED) response is 
given if the tax check has been sucessfully saved.

Example request body:
```json
{ 
   "taxCheckCode" : "YZFMDJYY9",
   "ggCredId" : "testCred1",
   "licenceType": "OperatorOfPrivateHireVehicles",
   "verifier": {
      "dateofbirth": "1922-12-01"
   },
   "expiresAfter": "2021-11-01",
   "createDate": "2021-11-12T11:14:26.801+01:00[Europe/London]",
   "taxCheckStartDateTime": "2021-11-12T11:14:26.801+01:00[Europe/London]",
   "isExtracted": true,
   "source": "Digital"
}
```

### `GET        /hec/test-only/tax-check/:taxCheckCode`
Unauthenticated test-only endpoint to retrieve tax check data for the given tax check code. If the tax check code
exists in the database a `200` (OK) response is returned. If no tax check data can be found for the tax check code, a 
`401` (NOT FOUND) response is given.

Example response body:
```json
{
   "taxCheckData": {
      "applicantDetails": {
         "ggCredId": "credId",
         "crn": "1234567890",
         "companyName": "Test Company"
      },
      "licenceDetails": {
         "licenceType": "OperatorOfPrivateHireVehicles",
         "licenceTimeTrading": "TwoToFourYears",
         "licenceValidityPeriod": "UpToOneYear"
      },
      "taxDetails": {
         "hmrcCTUTR": "1111111111",
         "ctStatus": {
            "ctutr": "1234567890",
            "startDate": "2020-10-01",
            "endDate": "2019-30-09"
         },
         "recentlyStaredTrading": "Yes"
      },
      "taxCheckStartDateTime": "2021-12-02T14:56:11.75Z[Europe/London]",
      "source": "Digital",
      "type": "Company"
   }
}
```

### `DELETE     /hec/test-only/tax-check`              
Unauthenticated test-only endpoint to delete all tax check data. Returns a `200` (OK) response if all data has been
successfully been deleted.

### `DELETE     /hec/test-only/tax-check/:taxCheckCode`
Unauthenticated test-only endpoint to delete tax check data corresponding to the given tax check code. Returns a `200` 
(OK) response if the tax check data has been successfully been deleted.

### `GET        /hec/test-only/file-transfer`
Unauthenticated test-only endpoint to immediately trigger a file transfer job (see next section for more details). 
The files that would have been created in the next scheduled file transfer will be created and be sent over. The next
scheduled job will still run at the configured time.


## HEC Tax Check Data File Transfer

This service will periodically send tax check data to consuming downstream systems via a file transfer process. 
`HecTaxCheckScheduleService` is the service which takes care of the job scheduling which perform the following tasks:
1. Identify the extraction time from the config.
2. TimeCalculator identifies how much time is left for the job to run and the job is scheduled for that time.
3. At the time of job run, a lock is created on mongo and the following operations are performed 
   1. Generate all enum lookup files, store them in `object-store` and notify downstream systems that the files are 
      ready to pick up.
   2. Find all tax checks which haven't been sent yet (`isExtracted = false`) and generate tax check data file,
      store in object store and notify downstream systems that the file is ready to pick up.      

The maximum number of tax checks per tax check file can be set in config. If there are more tax checks than the 
configured maximum the tax checks will be split into separate files. 

Once the downstream system has been notified that the files are ready to pick up we expect callbacks to be made 
to this service informing us of the progress of the consumption process. If the process has finished (successfully or 
unsuccessfully) we delete the corresponding files from `object-store` and mark the relevant tax checks as sent 
(`isExtracted = true`).  

### Testing file transfers locally
When testing file transfers locally, a command to create an internal-auth token must be made in order for the 
call to store files in `object-store` work. To make the default token in `application.conf` work, run this command:
```bash
curl -v -X POST --header "Content-Type: application/json"  --data '{ "token": "123457",  "principal": "hec", "permissions": [{ "resourceType": "object-store", "resourceLocation": "*", "actions": ["*"] }] }' http://localhost:8470/test-only/token 
```


## License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").
