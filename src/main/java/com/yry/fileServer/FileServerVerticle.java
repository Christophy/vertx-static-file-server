package com.yry.fileServer;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;

import java.util.ArrayList;
import java.util.List;


public class FileServerVerticle extends AbstractVerticle {

  public final Logger logger = LoggerFactory.getLogger(FileServerVerticle.class);
  private final String FILE_LOCATION = "/data/";
  private final Long BODY_LIMIT = 10*1024*1024L;
  @Override
  public void start(Promise<Void> startPromise) throws Exception {

    Router router = Router.router(vertx);
    // log all requests
    router.route().handler(route ->{
      logger.info("incoming query:"+ route.request().path());
      route.next();
    });
    // first handler to enable multipart/file
    BodyHandler bodyHandler = BodyHandler.create();
    bodyHandler.setHandleFileUploads(true).setDeleteUploadedFilesOnEnd(false)
            .setUploadsDirectory(FILE_LOCATION).setBodyLimit(BODY_LIMIT).setMergeFormAttributes(true);
    router.post("/upload").handler(bodyHandler);
    // upload logic
    router.post("/upload").handler(ctx -> {
      // end point for upload
      JsonArray resLocations = new JsonArray();
      List<Future> allMvFutures = new ArrayList<>();
      ctx.fileUploads().forEach(fileUpload -> {

        String fileName = fileUpload.fileName();
        String[] splitedFileName = fileName.split("\\.");
        String fileExtension = splitedFileName.length>1?splitedFileName[splitedFileName.length-1] :null;
        logger.info(splitedFileName.length);
        String uploadedFileName = fileExtension == null?fileUpload.uploadedFileName():fileUpload.uploadedFileName() + "." + fileExtension;
        JsonObject fileResult = new JsonObject();
        fileResult.put("uploadedFileName", uploadedFileName.replace(FILE_LOCATION, ""));
        fileResult.put("fileName", fileName);
        resLocations.add(fileResult);
        allMvFutures.add(vertx.fileSystem().move(fileUpload.uploadedFileName(), uploadedFileName));
      });
      CompositeFuture.all(allMvFutures).onComplete(futures -> {
        if(futures.succeeded()){
          logger.info("upload success.");
          ctx.response()
                  .putHeader("content-type", "application/json")
                  .end(resLocations.toString());
        } else {
          ctx.response().setStatusCode(500).end();
        }
      });
    });

    router.route(HttpMethod.GET, "/download/data/:fileName").handler(route -> {
      String file = route.pathParam("fileName");
      // end point for download
      route.response().sendFile(FILE_LOCATION + file);
    });

    HttpServerOptions options = new HttpServerOptions();
    options.setDecompressionSupported(true).setCompressionSupported(true);
    vertx.createHttpServer(options).requestHandler(router::handle).listen(8082, res -> {
      if (res.succeeded()) {
        logger.info("Start File Server Succeeded!");
        startPromise.complete();
      } else {
        logger.error("Start File Server Failed!", res.cause());
        startPromise.fail(res.cause());
      }
    });
  }

}
