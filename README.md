
# external-test-login-frontend

## Starting the service locally

Run it using the included shell script:

`./run_local_with_dependencies.sh`

This should start this service and any dependent services.

Once the script has finished, the service will be available on: http://localhost:19154/external-test-login-frontend/create

## Running tests

Run all of the unit tests with `sbt test`

Run all of the integration tests with `sbt it:test`

Run the unit and integration tests with code coverage reporting using `./run_all_tests.sh`

## Service responsibilities

This is the front-end for the functionality to create test Individuals or Organisations that can subsequently be used for testing of API microservices in the External Test environment.

## Who uses this service?

Users or traders who want to test an application in the external test environment.

### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").