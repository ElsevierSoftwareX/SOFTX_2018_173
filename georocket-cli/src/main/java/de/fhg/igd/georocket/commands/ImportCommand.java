package de.fhg.igd.georocket.commands;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;

import org.apache.commons.lang3.tuple.Pair;

import de.fhg.igd.georocket.util.DurationFormat;
import de.undercouch.citeclip.InputReader;
import de.undercouch.citeclip.OptionParserException;
import de.undercouch.citeclip.UnknownAttributes;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.file.OpenOptions;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.rx.java.ObservableFuture;
import io.vertx.rx.java.RxHelper;
import io.vertx.rxjava.core.Vertx;
import io.vertx.rxjava.core.file.AsyncFile;
import io.vertx.rxjava.core.file.FileSystem;
import io.vertx.rxjava.core.http.HttpClient;
import io.vertx.rxjava.core.http.HttpClientRequest;
import io.vertx.rxjava.core.streams.Pump;
import rx.Observable;

/**
 * Import one or more files into GeoRocket
 * @author Michel Kraemer
 */
public class ImportCommand extends AbstractGeoRocketCommand {
  private List<String> patterns;
  
  /**
   * Set the patterns of the files to import
   * @param patterns the file patterns
   */
  @UnknownAttributes("FILE PATTERN")
  public void setPatterns(List<String> patterns) {
    this.patterns = patterns;
  }
  
  @Override
  public String getUsageName() {
    return "import";
  }

  @Override
  public String getUsageDescription() {
    return "Import one or more files into GeoRocket";
  }
  
  @Override
  public boolean checkArguments() {
    if (patterns == null || patterns.isEmpty()) {
      error("no file pattern given. provide at least one file to import.");
      return false;
    }
    return super.checkArguments();
  }

  @Override
  public void doRun(String[] remainingArgs, InputReader in, PrintWriter out,
      Handler<Integer> handler) throws OptionParserException, IOException {
    long start = System.currentTimeMillis();
    
    Vertx vertx = new Vertx(this.vertx);
    
    HttpClientOptions options = new HttpClientOptions().setKeepAlive(true);
    HttpClient client = vertx.createHttpClient(options);
    
    Queue<String> files = new ArrayDeque<>(patterns);
    doImport(files, client, vertx, exitCode -> {
      client.close();
      
      if (exitCode == 0) {
        String m = "file";
        if (patterns.size() > 1) {
          m += "s";
        }
        System.out.println("Successfully imported " + patterns.size() + " " +
            m + " in " + DurationFormat.formatUntilNow(start));
      }
      
      handler.handle(exitCode);
    });
  }
  
  /**
   * Import files using a HTTP client and finally call a handler
   * @param files the files to import
   * @param client the HTTP client
   * @param vertx the Vert.x instance
   * @param handler the handler to call when all files have been imported
   */
  private void doImport(Queue<String> files, HttpClient client, Vertx vertx,
      Handler<Integer> handler) {
    if (files.isEmpty()) {
      handler.handle(0);
      return;
    }
    
    String path = files.poll();
    
    // open file
    FileSystem fs = vertx.fileSystem();
    OpenOptions openOptions = new OpenOptions().setCreate(false).setWrite(false);
    fs.openObservable(path, openOptions)
      // print file name
      .map(f -> {
        System.out.print("Importing " + Paths.get(path).getFileName() + " ... ");
        return f;
      })
      // get file size
      .flatMap(f -> fs.propsObservable(path).map(props -> Pair.of(f, props.size())))
      // import file
      .flatMap(f -> importFile(f.getLeft(), f.getRight(), client))
      .map(v -> {
        System.out.println("done");
        return v;
      })
      // handle response
      .subscribe(v -> {
        doImport(files, client, vertx, handler);
      }, err -> {
        System.out.println("error");
        error(err.getMessage());
        handler.handle(1);
      });
  }
  
  /**
   * Upload a file to GeoRocket
   * @param f the file to upload (will be closed at the end)
   * @param fileSize the file's size
   * @param client the HTTP client to use
   * @return an observable that will emit when the file has been uploaded
   */
  private Observable<Void> importFile(AsyncFile f, long fileSize, HttpClient client) {
    ObservableFuture<Void> o = RxHelper.observableFuture();
    Handler<AsyncResult<Void>> handler = o.toHandler();
    
    HttpClientRequest request = client.post(63074, "localhost", "/db");
    
    request.putHeader("Content-Length", String.valueOf(fileSize));
    
    Pump pump = Pump.pump(f, request);
    f.endHandler(v -> {
      f.close();
      request.end();
    });
    
    Handler<Throwable> exceptionHandler = t -> {
      f.endHandler(null);
      f.close();
      request.end();
      handler.handle(Future.failedFuture(t));
    };
    f.exceptionHandler(exceptionHandler);
    request.exceptionHandler(exceptionHandler);
    
    request.handler(response -> {
      if (response.statusCode() != 202) {
        handler.handle(Future.failedFuture("GeoRocket did not accept the file "
            + "(status code " + response.statusCode() + ": " + response.statusMessage() + ")"));
      } else {
        handler.handle(Future.succeededFuture());
      }
    });
    
    pump.start();
    
    return o;
  }
}
