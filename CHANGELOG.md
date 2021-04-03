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

## [0.0.6.1]
### Maintenance
#### Bump versions
- Vert.x > 3.9.6
- Kotlin > 1.4.32
- Kotlin coroutines > 1.4.3
