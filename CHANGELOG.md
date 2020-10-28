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
