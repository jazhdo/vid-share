package mc.jazhdo;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.SQLException;
import java.time.Duration;

import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;

public class DB {
    private static final Logger log = Global.log;
    private static final String cloudflareWorkerAPI = "http://localhost:8787/api/";

    public static JSONObject execute(String statement) throws SQLException {
        return execute(new SQLStatement(statement));
    }
    // Helper function to perform a query on the cloudflare d1 database (Documentation at https://developers.cloudflare.com/api/resources/d1/subresources/database/methods/query/)
    public static JSONObject execute(SQLStatement statement) throws SQLException {
        JSONObject body = new JSONObject();
        body.put("sql", statement.sql());
        body.put("params", statement.params());

        HttpResponse<String> res = doNetReq(cloudflareWorkerAPI + "db/prepare", body.toString());

        log.info("Request returned the following string:\n" + res.body());
        
        return new JSONObject(res.body());
    }
    public static JSONObject execute(SQLStatement... statements) throws SQLException {
        JSONArray batch = new JSONArray();
        for (SQLStatement statement : statements) {
            JSONObject item = new JSONObject();
            item.put("sql", statement.sql());
            item.put("params", new JSONArray(statement.params()));
            batch.put(item);
        }

        HttpResponse<String> res = doNetReq(cloudflareWorkerAPI + "db/batch", batch.toString());

        log.info("Batch request returned the following string:\n" + res.body());

        return new JSONObject(res.body());
    }
    public static JSONObject executeUpdate(String statement) throws SQLException {
        return executeUpdate(new SQLStatement(statement));
    }
    public static int executeUpdate(SQLStatement statement) throws SQLException {
        JSONObject body = new JSONObject();
        body.put("sql", statement.sql());
        body.put("params", statement.params());

        HttpResponse<String> res = doNetReq(cloudflareWorkerAPI + "db/update", body.toString());

        log.info("Update request returned the following string:\n" + res.body());

        return Integer.parseInt(res.body());
    }
    private static HttpResponse<String> doNetReq(String url, String body) throws SQLException {
        HttpRequest req;
        try {
            req = HttpRequest.newBuilder(new URI(url))
                .timeout(Duration.ofSeconds(30))
                .headers("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        } catch (URISyntaxException ex) {
            throw new SQLException();
        }

        HttpResponse<String> res;
        try {
            res = HttpClient.newBuilder().build().send(req, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException ex)  {
            throw new SQLException();
        }

        return res;
    }
    public static String req(String path, JSONObject body) throws SQLException {
        HttpResponse<String> res = doNetReq(cloudflareWorkerAPI + path, body.toString());
        return res.body();
    }
    public static record SQLStatement(String sql, Object... params) {}
}