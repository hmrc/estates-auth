# Estates auth

This service is a fasacde in front of auth and agent-access-control, it is responsible for authenticating users who want to register and maintian an estate.

To run locally using the micro-service provided by the service manager:

***sm --start ESTATES_ALL -r***

If you want to run your local copy, then stop the frontend ran by the service manager and run your local code by using the following (port number is 8836 but is defaulted to that in build.sbt).

`sbt run`

### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").
