# README

![demo.png](docs/images/demo.png)

Docker image
```bash
docker pull gbhat618/jenkins-to-sql:amd64
```

Export these environment variables,

```bash
# db url should have all parameters, server hostname, port, dbname, user, password
DB_URL=jdbc:postgresql://localhost:5432/postgres?user=postgres&password=pass

# jenkins home location
JENKINS_HOME=/Users/gbhat/sandbox/controller/data/jhome

# interval to scrape the new jobs or new builds and write it to postgres
POLL_INTERVAL_SECONDS=10
```

## Architecture

* recursively finds jobs (jobs in folders also supported)
* finds builds since the last seen build (which is noted in postgresql already)
* for builds that don't have a terminal status (`ABORTED`, `FAILURE`, `SUCCESS`), it will try to find the status in every periodic run.

## To be done

* batch writing to postgres
* improve efficiency in general
