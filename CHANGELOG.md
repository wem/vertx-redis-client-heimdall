# Changelog

## [0.0.2]
### Improved
#### Cleanup before reconnect (hardening)
- Move cleanup code in the common client to appropriate function and call this super function in subscription client.
- Close subscription connection before reconnect.  
#### Closing (hardening)
- Subscription client will do each closing step in catched fashion, so a single fail will not skip subsequent closing tasks.
### Fixed
#### Closing issue (hardening)
- Add missing async result handler call on context closing hook.

## [0.0.3]
### Fixed
#### Handle client is busy (hardening)
- Cover the case if the user is trying to execute too many commands at once -> ConnectionPoolTooBusyException. 
A RedisHeimdallException (with CLIENT_BUSY reason) exception will get thrown, and the reconnecting process will NOT get started.
 
## [0.0.4]
### Fixed
#### Handle closed channel exception (hardening)
- Closed channel exception is now proper handled and will initiate the reconnecting process.
### Improvement
#### Previous delegated client closed later
- In some scenarios, like under heavy load and retry like usage of the Heimdall client in the case of failure. It could be
the case that's during the reconnection process still some commands are in flight (they executed before reconnect) and are able to success.
Now the Heimdall client will keep the previous delegated client open until a "fresh", reconnected delegate is available.
 
## [0.0.5]
### Feature
#### Support for pattern subscriptions
- The subscription client now have full support for PSUBSCRIBE.
### Improvement
#### RedisHeimdallSubscriptionOptions
- Introducing of options class for the subscription client.
 
## [0.0.6]
### Fix
#### Jackson serialization issue of Options
- Jackson deserialization issues with Options.

## [1.0.0]
### Feature
#### Vert.x 4
- Migration to Vert.x 4

## [1.0.1]
### Fixed
#### Handle "Broken pipe" IOException as connection issue and initiate reconnect
- Thrown java.io.IOException with message "Broken pipe" will be handled now as connection issue and therefore the client
  will reconnect.
### Maintenance
#### Bump versions
- Vert.x: 4.1.0
- Kotlin: 1.4.32 / Coroutines 1.4.3
#### Moved to Maven central
Because Bintray will become recently deprecated, this project is now available on Maven central. Please check README.
#### GroupId changed
To be more consistent over all Vert.x related Source-motion projects, we did change the groupId to `ch.sourcemotion.vertx`.

## [1.1.0]
### Feature
#### RedisHeimdallLight
- This library now provides a single connection client, with the same capabilities as the common client (reconnect, etc.).
  Please check the README.

## [1.2.0]
### Improvement
#### OWASP dependency check
- Integrate OWASP dependency check Gradle plugin
### Maintenance
#### Bump versions
- Vert.x: 4.2.2
- Kotlin: 1.6.10 / Coroutines 1.5.2
#### Code cleanup and tests
- Removed obsolete code and implement missing test
