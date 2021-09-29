
# hec

This is a placeholder README.md for a new repository

### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").

### HEC Tax Check Data Scheduler job:
HecTaxCheckScheduleService is the service which takes care of the job scheduling which perform the following tasks:
1) Identify the extraction time from the config.
2) TimeCalculator identifies how much time is left for the ob to run  and the job is scheduled for that time.
3) At the time of job run, a lock is created on mongo and the following operations are performed on the "hec-tax-check" collection
   1) Fetch all records from hec-tax-check with isExtracted as false means they were not extracted in the last run.
   2) Generate a file with all the relevant data. ( Will be a part of another ticket)
   3) Update isExtracted to true for all records processed.
4) Once the job is done, schedule the job again for next run.

