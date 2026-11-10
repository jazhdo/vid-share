package mc.jazhdo;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublisher;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpRequest.Builder;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandler;
import java.net.http.HttpResponse.BodyHandlers;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Arrays;
import java.util.function.Function;

import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;

@SuppressWarnings("CallToPrintStackTrace")
public class DB {
    private static final Logger log = Global.log;
    private static final String CLOUDFLARE_WORKER_API = System.getenv("CLOUDFLARE_WORKER_API");
    
    // Helper functions to perform queries on the cloudflare d1 database (Documentation at https://developers.cloudflare.com/api/resources/d1/subresources/database/methods/query/)

    // PreparedStatement execution
    public static JSONArray execute(String statement) throws SQLException {
        return execute(new SQLStatement(statement));
    }
    public static JSONArray execute(SQLStatement statement) throws SQLException {
        JSONObject body = new JSONObject();
        body.put("sql", statement.sql());
        body.put("params", statement.params());

        HttpResponse<String> res = post("db/prepare", BodyPublishers.ofString(body.toString()), BodyHandlers.ofString(), "Content-Type", "application/json");

        log.info("Request returned the following string:\n" + res.body());
        
        return new JSONArray(res.body());
    }
    public static JSONArray execute(String... statements) throws SQLException {
        return execute(Arrays.stream(statements).map((statement) -> new SQLStatement(statement)).toArray(SQLStatement[]::new));
    }
    public static JSONArray execute(SQLStatement... statements) throws SQLException {
        JSONArray batch = new JSONArray();
        for (SQLStatement statement : statements) {
            JSONObject item = new JSONObject();
            item.put("sql", statement.sql());
            item.put("params", new JSONArray(statement.params()));
            batch.put(item);
        }

        HttpResponse<String> res = post("db/batch", BodyPublishers.ofString(batch.toString()), BodyHandlers.ofString(), "Content-Type", "application/json");

        log.info("Batch request returned the following string:\n" + res.body());

        return new JSONArray(res.body());
    }
    
    // PrepareStatement execution but only for the first elemente
    public static JSONObject executeFirst(SQLStatement statement) throws SQLException {
        JSONObject body = new JSONObject();
        body.put("sql", statement.sql());
        body.put("params", statement.params());

        HttpResponse<String> res = post("db/first", BodyPublishers.ofString(body.toString()), BodyHandlers.ofString(), "Content-Type", "application/json");

        String result = res.body();

        log.info("First request returned the following string:\n" + result);

        return result == null ? null : new JSONObject(result);
    }

    // Updates
    public static int executeUpdate(String statement) throws SQLException {
        return executeUpdate(new SQLStatement(statement));
    }
    public static int executeUpdate(SQLStatement statement) throws SQLException {
        JSONObject body = new JSONObject();
        body.put("sql", statement.sql());
        body.put("params", statement.params());

        HttpResponse<String> res = post("db/update", BodyPublishers.ofString(body.toString()), BodyHandlers.ofString(), "Content-Type", "application/json");

        log.info("Request returned the following string:\n" + res.body());
        
        return Integer.parseInt(res.body());
    }

    // Raw statement execution
    public static int executeStatement(String statement) throws SQLException {
        HttpResponse<String> res = post("db/exec", BodyPublishers.ofString(statement), BodyHandlers.ofString());

        log.info("Update request returned the following string:\n" + res.body());

        return Integer.parseInt(res.body());
    }

    // Just requests and their method shortcuts
    public static <T> HttpResponse<T> post(String path, BodyPublisher body, BodyHandler<T> handler, String... headers) throws SQLException {
        return req(path, (builder) -> builder.POST(body), handler, headers);
    }
    public static <T> HttpResponse<T> get(String path, BodyHandler<T> handler, String... headers) throws SQLException {
        return req(path, (builder) -> builder.GET(), handler, headers);
    }
    public static <T> HttpResponse<T> query(String path, BodyPublisher body, BodyHandler<T> handler, String... headers) throws SQLException {
        return req(path, (builder) -> builder.method("QUERY", body), handler, headers);
    }
    public static <T> HttpResponse<T> req(String path, Function<Builder, Builder> method, BodyHandler<T> handler, String... headers) throws SQLException {
        HttpRequest req;
        try {
            Builder temp = method.apply(HttpRequest.newBuilder(new URI(CLOUDFLARE_WORKER_API + path))).timeout(Duration.ofSeconds(30));
            if (headers.length > 1) temp.headers(headers);
            req = temp.build();
        } catch (URISyntaxException ex) {
            ex.printStackTrace();
            throw new SQLException();
        }

        HttpResponse<T> res;
        try {
            res = HttpClient.newBuilder().build().send(req, handler);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            ex.printStackTrace();
            throw new SQLException();
        } catch (IOException ex)  {
            ex.printStackTrace();
            throw new SQLException();
        }

        return res;
    }

    public static record SQLStatement(String sql, Object... params) {}
}