# custom-rate-limiter

A reusable, annotation-driven, Redis-backed rate limiting library for Spring
Boot applications, packaged as a proper Spring Boot **starter**.

```
custom-rate-limiter/
├── pom.xml                              (parent POM)
├── docker-compose.yml                   (Redis for local runs)
├── rate-limiter-spring-boot-starter/    (the reusable library)
└── demo-application/                    (a sample app consuming it)
```

## 1. What you get as a consumer

Add one dependency:

```xml
<dependency>
    <groupId>com.soham</groupId>
    <artifactId>rate-limiter-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

Annotate a controller method:

```java
@RateLimit(limit = 100, windowSeconds = 60, algorithm = Algorithm.TOKEN_BUCKET)
@GetMapping("/books")
public List<Book> getBooks() {
    return books;
}
```

Callers identify themselves with an `X-User-Id` header. Requests within the
limit pass through untouched; requests over the limit get:

```
HTTP/1.1 429 Too Many Requests
Content-Type: application/json

{"message": "Rate limit exceeded"}
```

Nothing else is required. No beans, no Redis client setup, no
`@EnableXxx` annotation on the consuming application — everything is wired
automatically by Spring Boot autoconfiguration.

## 2. Architecture

```
                 ┌─────────────────────────┐
  HTTP request   │      BookController     │
 ───────────────▶│  @RateLimit(...)        │
                 └───────────┬─────────────┘
                             │ (method call intercepted)
                             ▼
                 ┌─────────────────────────┐
                 │     RateLimitAspect     │  Spring AOP @Before advice.
                 │  (reads annotation,     │  Pure adapter: no business logic.
                 │   HTTP request)         │
                 └───────────┬─────────────┘
                             │ isAllowed(userId, endpoint, limit, window, algorithm)
                             ▼
                 ┌─────────────────────────┐
                 │    RateLimiterService   │  Orchestration only.
                 └───────────┬─────────────┘
                             │ getStrategy(algorithm)
                             ▼
                 ┌─────────────────────────┐
                 │  RateLimitStrategyFactory│  Map<Algorithm, RateLimitStrategy>
                 └───────────┬─────────────┘
                             │
             ┌───────────────┼────────────────┬─────────────────┐
             ▼               ▼                ▼                 ▼
      FixedWindow      SlidingWindow     TokenBucket       LeakyBucket
       Strategy          Strategy          Strategy          Strategy
             │               │                │                 │
             └───────────────┴────────┬───────┴─────────────────┘
                                      ▼
                                    Redis
```

Each layer has one job (Single Responsibility Principle):

| Component | Responsibility |
|---|---|
| `@RateLimit` | Declarative configuration on a handler method |
| `RateLimitAspect` | Spring AOP interception + HTTP/reflection plumbing only |
| `RateLimiterService` | Business orchestration: pick a strategy, ask it a yes/no question |
| `RateLimitStrategyFactory` | Resolves `Algorithm -> RateLimitStrategy` via a `Map`, **no if/else chains** |
| `RateLimitStrategy` implementations | The actual algorithm, backed by Redis |
| `RateLimiterAutoConfiguration` | Wires every bean above together automatically |
| `RateLimitExceededException` + `RateLimitExceptionHandler` | Translates a rejected request into HTTP 429 |

### Why the Strategy Pattern

`RateLimitStrategyFactory` holds a `Map<Algorithm, RateLimitStrategy>` built
once in `RateLimiterAutoConfiguration`. Nothing in the factory, the service,
or the aspect ever branches on which algorithm is active — they all just call
`strategy.allowRequest(...)`. This satisfies the Open/Closed Principle: the
system is open for extension (new algorithms) but closed for modification
(no existing class changes when one is added).

## 3. How the annotation works, end to end

1. `RateLimitAspect` is an AspectJ `@Aspect` bean, registered by
   `RateLimiterAutoConfiguration`. Spring's AOP auto-proxy machinery wraps
   any bean whose method carries `@RateLimit` in a proxy.
2. On each call, the `@Before("@annotation(rateLimit)")` advice runs before
   the real controller method:
   - Reads the current `HttpServletRequest` via `RequestContextHolder` and
     pulls the `X-User-Id` header (falls back to `"anonymous"` if absent).
   - Builds an endpoint identifier from the intercepted method
     (`ClassName#methodName`).
   - Delegates to `RateLimiterService.isAllowed(...)`.
3. `RateLimiterService` asks `RateLimitStrategyFactory` for the strategy
   matching the annotation's `algorithm()` value, and calls
   `allowRequest(userId, endpoint, config)` on it.
4. Each strategy reads/writes its own state shape in Redis (counters,
   sorted sets, or hashes — see the Javadoc on each class) and returns
   `true`/`false`.
5. If `false`, the aspect throws `RateLimitExceededException`.
   `RateLimitExceptionHandler` (a `@RestControllerAdvice` bean, also
   auto-registered) catches it and returns HTTP 429 with the documented
   JSON body — before the real controller method ever runs.

Because the state lives in Redis rather than JVM memory, the limit is
enforced correctly even when the consuming application is scaled out to
multiple instances behind a load balancer.

## 4. Redis key layout

Every strategy uses `RedisKeyGenerator` so keys always look like:

```
rate_limit:<algorithm>:<userId>:<endpoint>
```

e.g. `rate_limit:fixed_window:soham:BookController#getBooks`. Including the
algorithm name means switching a method's `algorithm` attribute never
collides with counters an earlier configuration left behind.

| Algorithm | Redis structure | Notes |
|---|---|---|
| `FIXED_WINDOW` | `STRING` counter + `TTL` | `INCR`, TTL set only on the first increment |
| `SLIDING_WINDOW` | `ZSET` of request timestamps | Old entries trimmed with `ZREMRANGEBYSCORE`, counted with `ZCARD` |
| `TOKEN_BUCKET` | `HASH` (`tokens`, `lastRefill`) | Atomic read-refill-decrement via a Lua script |
| `LEAKY_BUCKET` | `HASH` (`level`, `lastLeak`) | Atomic read-leak-increment via a Lua script |

Token bucket and leaky bucket use Lua scripts (`EVAL`) so the
read-modify-write sequence is atomic even when multiple application
instances hit Redis concurrently — a plain Java "read, compute, write"
would race under concurrent load.

## 5. How another Spring Boot app uses this dependency

1. `mvn install` this project (or publish `rate-limiter-spring-boot-starter`
   to your artifact repository).
2. Add the dependency shown in section 1 to your app's POM.
3. Make sure `spring.data.redis.host` / `spring.data.redis.port` point at a
   reachable Redis (Spring Boot's own Redis autoconfiguration handles the
   connection factory; this starter reuses whatever `StringRedisTemplate`
   Spring Boot creates from those properties).
4. Annotate any `@GetMapping`/`@PostMapping`/etc. method with `@RateLimit`.
5. Send requests with an `X-User-Id` header.

That's it — see `demo-application` for a complete, minimal example.

## 6. How to add a new rate limiting algorithm

No existing class needs to change. Three steps:

1. **Add an enum constant** in `Algorithm`:
   ```java
   public enum Algorithm {
       FIXED_WINDOW, SLIDING_WINDOW, TOKEN_BUCKET, LEAKY_BUCKET,
       GCRA // <-- new
   }
   ```
2. **Implement the strategy**:
   ```java
   public class GcraStrategy implements RateLimitStrategy {
       @Override
       public boolean allowRequest(String userId, String endpoint, RateLimitConfig config) {
           // your Redis-backed logic here
       }
   }
   ```
3. **Register it** in `RateLimiterAutoConfiguration` — add a `@Bean` method
   for it and one line in the map inside `rateLimitStrategyFactory(...)`:
   ```java
   strategies.put(Algorithm.GCRA, gcraStrategy);
   ```

Nothing in `RateLimitAspect`, `RateLimiterService`, or
`RateLimitStrategyFactory` changes. Consumers immediately get the new
algorithm by writing `algorithm = Algorithm.GCRA` on their `@RateLimit`
annotation.

## 7. Running Redis locally with Docker

```bash
docker compose up -d
```

This starts Redis 7 on `localhost:6379` (matching `demo-application`'s
`application.yml`). Stop it with:

```bash
docker compose down
```

## 8. Running the demo application

```bash
docker compose up -d          # start Redis
cd demo-application
mvn spring-boot:run
```

Then, from another terminal:

```bash
# Allowed (well within the limit of 5/60s on FIXED_WINDOW)
curl -i http://localhost:8080/books -H "X-User-Id: soham"

# Hit it 6 times in a row to see HTTP 429 on the 6th
for i in $(seq 1 6); do curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/books -H "X-User-Id: soham"; done

# A TOKEN_BUCKET endpoint (limit = 3 per 30s) to see burst behavior
curl -i http://localhost:8080/books/burstable -H "X-User-Id: soham"
```

## 9. Testing

```bash
mvn clean test
```

- `rate-limiter-spring-boot-starter` tests run each strategy against an
  embedded, in-memory Redis (`jedis-mock`) — no Docker required to run unit
  tests:
  - `FixedWindowStrategyTest`, `SlidingWindowStrategyTest`,
    `TokenBucketStrategyTest`, `LeakyBucketStrategyTest` — per-algorithm
    allow/reject/isolation behavior.
  - `RateLimitStrategyFactoryTest` — correct resolution and algorithm
    switching, plus the error path for an unregistered algorithm.
  - `RateLimitAspectTest` — annotation interception, `X-User-Id` extraction
    (including the anonymous fallback), and that a rejected request throws
    `RateLimitExceededException`.
- `demo-application` has `RateLimitIntegrationTest` — a full
  `@SpringBootTest` + `MockMvc` test proving the whole chain (HTTP request
  → AOP interception → Redis-backed strategy → HTTP 429) with **zero**
  rate-limiter beans declared in the demo app itself.

## 10. Design principles applied

- **Strategy Pattern** — `RateLimitStrategy` + four interchangeable
  implementations.
- **Open/Closed Principle** — new algorithms are additive; no existing
  class is modified.
- **Single Responsibility Principle** — annotation, aspect, service,
  strategy, and exception handling are all separate, independently
  testable classes.
- **Dependency Injection** — every bean is constructor-injected; strategies
  and the factory are plain classes with no framework dependencies beyond
  `StringRedisTemplate`.
- **Convention over configuration** — `RateLimiterAutoConfiguration` +
  `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
  make this a real, self-wiring Spring Boot starter; consuming
  applications never construct a rate-limiter bean by hand.

## 11. Building

```bash
mvn clean install
```

> This environment's sandbox network couldn't reach Maven Central to run a
> full `mvn clean install` while generating this project, so please run the
> build locally to confirm everything resolves — the code has been written
> and reviewed carefully, but hasn't had an actual compiler pass over it.
> If `jedis-mock`'s `RedisServer` API differs slightly from what's used in
> `RedisTestSupport`/`RateLimitIntegrationTest` for the pinned version
> (`1.1.5`), those two test-support spots are the only ones likely to need
> a small tweak.
