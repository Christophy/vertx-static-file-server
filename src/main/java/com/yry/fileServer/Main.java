package com.yry.fileServer;
import io.vertx.core.Vertx;

public class Main {
  public static void main(String[] args){
    Vertx vertx = Vertx.vertx();
    vertx.deployVerticle("com.yry.fileServer.FileServerVerticle");
  }
}
