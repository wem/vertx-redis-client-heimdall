# vertx-redis-client-heimdall

Vert.x Redis client, based on the official one: https://vertx.io/docs/vertx-redis-client/java/

This client enhances the original project with reconnect capabilities.

## Installation
![Gradle](doc/gradle.png)
```groovy
repositories {
    mavenCentral()
}

implementation "ch.sourcemotion.vertx:vertx-redis-client-heimdall:[version]"
```

![Gradle](doc/gradle.png) (Kotlin DSL)
```kotlin
repositories {
    mavenCentral()
}

implementation("ch.sourcemotion.vertx:vertx-redis-client-heimdall:[version]")
```
> To find the most recent version you could use the following link
> https://search.maven.org/search?q=g:ch.sourcemotion.vertx%20a:vertx-redis-client-heimdall
  
![Maven](doc/maven.png)
```xml
<repositories>
    <repository>
        <id>central</id>
        <name>Maven Central</name>
        <url>https://repo1.maven.org/maven2</url>
    </repository>
</repositories>

<dependency>
    <groupId>ch.sourcemotion.vertx</groupId>
    <artifactId>vertx-redis-client-heimdall</artifactId>
    <version>[version]</version>
</dependency>
```

## Usage
### Configuration

The configuration class `ch.sourcemotion.vertx.redis.client.heimdall.RedisHeimdallOptions` inherits from the original 
project, so the basic options are similar. The additional reconnect options have default values, so basically
the client is ready to run out of the box. Please refer Javadoc of the options class for details 
[Vert.x Redis Heimdall options](https://github.com/wem/vertx-redis-client-heimdall/blob/main/src/main/kotlin/ch/sourcemotion/vertx/redis/client/heimdall/RedisHeimdallOptions.kt).

#### Jackson
As this client implemented in Kotlin you should register the Jackson object mapper Kotlin module if you serialize
`ch.sourcemotion.vertx.redis.client.heimdall.RedisHeimdallOptions` or `ch.sourcemotion.vertx.redis.client.heimdall.subscription.RedisHeimdallSubscriptionOptions`.
This could be done with `DatabindCodec.mapper().registerKotlinModule()`.

### General purpose client

The client with the same basic functionality as the original client (additionally with reconnect capabilities) can be instantiated as follows.

**Java**
```java
final RedisHeimdall redisHeimdall = RedisHeimdall.create(vertx, new RedisHeimdallOptions());
```

**Kotlin**
```kotlin
val redisHeimdall = RedisHeimdall.create(vertx, RedisHeimdallOptions())
```

> Please note, that the reconnected capability of the client works on a connection reference very limited.
> Means if you work directly on a connection reference, the reconnecting process will get started too, but
> the connection itself is not abstracted this way that it will be reusable after reconnect. So you must get a
> new connection reference from the client after reconnected.
>
> **So it's recommended to work on the client and not a connection directly when ever possible.**  

### Light client

Client that uses a single connection to Redis and bypasses any pooling etc. It also provides reconnect capabilities
as it uses RedisHeimdall under the hood.

**Java**
```java
final RedisHeimdall redisHeimdallLight = RedisHeimdall.createLight(vertx, new RedisHeimdallOptions());
```

**Kotlin**
```kotlin
val redisHeimdallLight = RedisHeimdall.createLight(vertx, RedisHeimdallOptions())
```

> This client will probably provide the best performance unless you need connection pooling.

#### Command queueing during initial connect
Commands against Redis getting queued until connected. On successful connect they are executed. If the initial connect fails, 
the callers will fail fast. There is no queueing for a later, delayed, successful connect. 

### Subscription client

An additional feature of this client is a specialized variant for subscription purposes only.
[ch.sourcemotion.vertx.redis.client.heimdall.subscription.RedisHeimdallSubscription](https://github.com/wem/vertx-redis-client-heimdall/blob/main/src/main/kotlin/ch/sourcemotion/vertx/redis/client/heimdall/subscription/RedisHeimdallSubscription.kt).

This client is designed to use it for subscriptions only, but also with reconnect capabilities as well.

**Java**
```java
final RedisHeimdallSubscriptionOptions options = new RedisHeimdallSubscriptionOptions().addChannelNames("channel-to-subscribe").addChannelPatterns(""channel-pattern-to-subscribe"")
final Handler<SubscriptionMessage> messageHandler = message -> {
    // Will called for message(s) by Redis subscription
};
final Future<RedisHeimdallSubscription> subscriptionFuture = RedisHeimdallSubscription.create(vertx, options, messageHandler);
```

**Kotlin**
```kotlin
val options = RedisHeimdallSubscriptionOptions().addChannelNames("channel-to-subscribe").addChannelPatterns("channel-pattern-to-subscribe")
val messageHandler = Handler<SubscriptionMessage> {
    // Will called for message(s) by Redis subscription
}
val client = RedisHeimdallSubscription.create(vertx, options, messageHandler).await()
```

#### Start (channel subscription)
The subscription client will subscribe on the passed channel names immediately after instantiation.
**If you don't want to subscribe on any channels at this point you can pass an empty channel name list.**

#### Add / remove channels (patterns)  to / from subscription
The subscription client provides functions to add and remove channels during runtime.

**Add**
```kotlin
fun addChannels(vararg channelNames: String): Future<Response>
fun addChannelPatterns(vararg channelPatterns: String): Future<Response>
```

Suspend variant
``` kotlin
suspend fun addChannelsAwait(vararg channelNames: String): RedisHeimdallSubscription
suspend fun addChannelPatternsAwait(vararg channelPatterns: String): RedisHeimdallSubscription
```

**Remove**
```kotlin
fun removeChannels(vararg channelNames: String): Future<Response>
fun removeChannelPatterns(vararg channelPatterns: String): Future<Response>
```

Suspend variant
```kotlin
suspend fun removeChannelsAwait(vararg channelNames: String): RedisHeimdallSubscription
suspend fun removeChannelPatternsAwait(vararg channelPatterns: String): RedisHeimdallSubscription
```

### Reconnect
If the option `ch.sourcemotion.vertx.redis.client.heimdall.RedisHeimdallOptions#reconnect` is set to true (default), 
the client will reconnect on connection issues automatically. 

**While reconnecting is in progress the client will decline any command against Redis. So an
`ch.sourcemotion.vertx.redis.client.heimdall.RedisHeimdallException` will be thrown immediately.**

For the subscription client all known (currently subscribed) channels will get registered again after a successful reconnect.

#### Causes for reconnect
The client should cover any situation of connection issues.
- If the TCP connection got lost
- When a command did signal with a connection issue.

#### Reconnecting process events over the event bus
Both client variants will notify you about the state of a reconnecting process.

##### Disable
If you have no need to get such notifications, you could disable this feature by setting false to
`ch.sourcemotion.vertx.redis.client.heimdall.RedisHeimdallOptions#reconnectingNotifications`

##### Reconnect start
If a client detected a connection issue an event will get send over the event bus according to configured address
`ch.sourcemotion.vertx.redis.client.heimdall.RedisHeimdallOptions#reconnectingStartNotificationAddress`

##### Reconnect successful
If the reconnecting process was successful, and the connectivity against Redis server(s) got established again
you will get notified on the event bus address.
`ch.sourcemotion.vertx.redis.client.heimdall.RedisHeimdallOptions#reconnectingSucceededNotificationAddress`

##### Reconnect failed
If the reconnecting process did fail (max attempts reached), or the reconnecting capability disabled on the client 
you will get notified on the event bus address.
`ch.sourcemotion.vertx.redis.client.heimdall.RedisHeimdallOptions#reconnectingFailedNotificationAddress`

### Exceptions
Only two types of exception will be propagated to the client user.
- `io.vertx.redis.client.impl.types.ErrorType` On Redis protocol and usage failures
- `ch.sourcemotion.vertx.redis.client.heimdall.RedisHeimdallException` On any other kind of cause

**Reasons**

The `ch.sourcemotion.vertx.redis.client.heimdall.RedisHeimdallException` has a property
`ch.sourcemotion.vertx.redis.client.heimdall.RedisHeimdallException#reason` which gives you more information
about the kind of error.

## Contribution and bug reports
Both is very welcome. :) ... Have fun.
