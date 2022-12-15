package demo;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.json.JsonObject;
import java.util.UUID;

public class HelloVerticle extends AbstractVerticle {

  @Override
  public void start() {
    String id = UUID.randomUUID().toString();
    vertx.eventBus().consumer("hello.vertx.addr", msg -> {
      msg.reply(
          new JsonObject().put("status", "running").put("success", true).put("id", id).toString());
    });

    vertx.eventBus().consumer("hello.named.addr", msg -> {
      String name = (String) msg.body();
      msg.reply(String.format("Hello %s! from %s", name, id));
    });
  }

}
