package demo;

import io.vertx.config.ConfigRetriever;
import io.vertx.config.ConfigRetrieverOptions;
import io.vertx.config.ConfigStoreOptions;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.CorsHandler;
import io.vertx.ext.web.handler.LoggerHandler;
import io.vertx.ext.web.handler.SessionHandler;
import io.vertx.ext.web.sstore.ClusteredSessionStore;

public class MainVerticle extends AbstractVerticle {

  @Override
  public void start(Future<Void> startFuture) {
    Router router = Router.router(vertx);

    ClusteredSessionStore sessionStore = ClusteredSessionStore.create(vertx);

    router.route().handler(LoggerHandler.create());
    router.route().handler(SessionHandler.create(sessionStore));
    router.route().handler(CorsHandler.create("localhost"));

    router.route().handler(ctx -> {
      String token = ctx.request().getHeader("AUTH");
      if (token != null && "secret".contentEquals(token)) {
        ctx.next();
      } else {
        ctx.response().setStatusCode(401).setStatusMessage("UNAUTHORIZED").end();
      }
    });

    router.get("/health").handler(this::healthCheck);
    router.get("/hello/:name").handler(this::helloName);

    doConfig(startFuture, router);
  }

  void doConfig(Future<Void> startFuture, Router router) {

    ConfigStoreOptions defaultConfig = new ConfigStoreOptions()
        .setFormat("json")
        .setType("file")
        .setConfig(new JsonObject().put("path", "config.json"));

    ConfigStoreOptions cliConfig = new ConfigStoreOptions()
        .setType("json")
        .setConfig(config());

    ConfigRetrieverOptions opts = new ConfigRetrieverOptions()
        .addStore(defaultConfig)
        .addStore(cliConfig);

    ConfigRetriever configRetriever = ConfigRetriever.create(vertx, opts);

    Handler<AsyncResult<JsonObject>> configHandler = res -> this.handleConfigResult(startFuture,
        router, res);

    configRetriever.getConfig(configHandler);
  }

  void handleConfigResult(Future<Void> startFuture, Router router, AsyncResult<JsonObject> res) {
    if (res.succeeded()) {
      JsonObject config = res.result();
      JsonObject http = config.getJsonObject("http");
      int httpPort = http.getInteger("port");

      JsonObject vertxOpts = config.getJsonObject("vertx");

      DeploymentOptions options = new DeploymentOptions()
          .setWorker(vertxOpts.getBoolean("worker"))
          .setInstances(vertxOpts.getInteger("noOfInstances"));

      vertx.deployVerticle("demo.HelloVerticle", options);

      vertx.createHttpServer().requestHandler(router).listen(httpPort);
      startFuture.complete();
    } else {
      System.out.println("Unable to load configs");
    }
  }

  void healthCheck(RoutingContext ctx) {
    vertx.eventBus().send("hello.vertx.addr", "", reply -> {
      ctx.request().response().end((String) reply.result().body());
    });
  }

  void helloName(RoutingContext ctx) {
    String name = ctx.request().getParam("name");
    vertx.eventBus().send("hello.named.addr", name, reply -> {
      System.out.println(name);
      ctx.response().end((String) reply.result().body());
      // ctx.response().end("Hello! " + name);
    });
  }

}
