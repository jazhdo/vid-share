package mc.jazhdo;

import java.io.IOException;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import jakarta.servlet.http.HttpServletRequest;
import mc.jazhdo.DB.SQLStatement;

@SuppressWarnings("CallToPrintStackTrace")
@RequestMapping("")
@Controller
public class PageManager {
    private final ResourceLoader resourceLoader;
    private final Logger log = LoggerFactory.getLogger(VidShareApplication.class);
    private final TemplateEngine templateEngine;
    private final Global global;

    public PageManager(Global global, ResourceLoader resourceLoader, TemplateEngine templateEngine) {
        this.global = global;
        this.resourceLoader = resourceLoader;
        this.templateEngine = templateEngine;
    }

    // Helper functions
    private String uppercaseFirst(String input) {
        return input.substring(0, 1).toUpperCase() + input.substring(1);
    }
    private String getPage(String pageName, Model model, Context pageContent) {
        model.addAttribute("page", new Page(uppercaseFirst(pageName), templateEngine.process(pageName + ".html", pageContent)));
        return "main";
    }
    public record Page(String name, String content) {};
    private String getErrorPage(HttpStatus status, Model model) {
        Context pageContext = new Context();
        String header = "Error " + Integer.toString(status.value()) + " " + switch (status) {
            case HttpStatus.BAD_REQUEST -> "Bad Request";
            case HttpStatus.NOT_FOUND -> "Not Found";
            case HttpStatus.MISDIRECTED_REQUEST -> "Misdirected Request";
            case HttpStatus.INTERNAL_SERVER_ERROR -> "Internal Server Error";
            default -> "Unknown Error";
        };
        pageContext.setVariable("header", header);
        return getPage("error", model, pageContext);
    }
    private String formatTimestamp(long timestamp) {
        return DateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.LONG).format(new Date(timestamp));
    }

    // Homepage
    @GetMapping(path = "/", produces = "text/html")
    public String getHome(Model model, HttpServletRequest request) {
        // Profile pic logic
        String uid = Global.getCookieValue("uid", request.getCookies());
        log.info("Request UID: {}", uid);
        model.addAttribute("pfp", uid != null ? uid : "default");

        Context context = new Context();
        try {
            JSONArray results = DB.execute("SELECT v.id, v.thumbnail, v.title, v.description, v.timestamp, u.id AS uid, u.displayname FROM videos v JOIN users u ON v.uid = u.id ORDER BY v.rowid DESC LIMIT 50");
            List<HomepageVideo> videos = new ArrayList<>();
            for (int i = 0; i < results.length(); i++) {
                JSONObject result = results.getJSONObject(i);
                videos.add(new HomepageVideo(
                    "/watch?v=" + result.getString("id"), result.getString("thumbnail"), result.getString("title"), result.getString("description"),
                    "Uploaded " + formatTimestamp(result.getLong("timestamp")), result.getString("uid"), result.getString("displayname")
                ));
            }
            context.setVariable("videos", videos);
        } catch (SQLException ex) {
            ex.printStackTrace();
            return getErrorPage(HttpStatus.INTERNAL_SERVER_ERROR, model);
        }
        return getPage("home", model, context);
    }
    public record HomepageVideo(String link, String thumbnail, String title, String description, String date, String uid, String displayname) {}

    // All other pages
    @GetMapping(path = "/{page}", produces = "text/html")
    public String getNormalPage(@PathVariable String page, Model model, HttpServletRequest request) {
        // Profile pic logic
        String uid = Global.getCookieValue("uid", request.getCookies());
        log.info("Request UID: {}", uid);
        model.addAttribute("pfp", uid != null ? uid : "default");

        return switch (page) {
            case "search" -> {
                String query = request.getParameter("q");
                if (query != null) {
                    List<SearchResult> searchResults = new ArrayList<>();
                    try {
                        JSONArray results = new JSONArray(DB.query("db/search", BodyPublishers.ofString(query), BodyHandlers.ofString()).body());
                        for (int i = 0; i < results.length(); i++) {
                            JSONObject result = results.getJSONObject(i);
                            searchResults.add(new SearchResult(
                                "/watch?v=" + result.getString("id"), result.getString("thumbnail"), result.getString("title"), result.getString("description"), result.getString("uid"),
                                result.getString("displayname"), result.getInt("views"), result.getInt("likes"), 0, formatTimestamp(result.getLong("timestamp"))
                            ));
                        }
                    } catch (SQLException ex) {
                        ex.printStackTrace();
                        log.warn("Search Error: {}", ex.getMessage());
                        yield getErrorPage(HttpStatus.INTERNAL_SERVER_ERROR, model);
                    }
                    Context context = new Context();
                    context.setVariable("results", searchResults);
                    context.setVariable("search", new SearchData(query, searchResults.isEmpty()));
                    yield getPage("search", model, context);
                } else yield getErrorPage(HttpStatus.BAD_REQUEST, model);
            }
            case "login" -> getPage(page, model, new Context());
            case "watch" -> {
                Context context = new Context();
                String videoID = request.getParameter("v");
                log.info("Watching video: {}", videoID);
                context.setVariable("id", videoID);
                yield getPage("watch", model, context);
            }
            case "make" -> {
                if (global.checkAuth(request)) yield getPage(page, model, new Context());
                else yield getPage("login", model, new Context());
            }
            case "profile" -> {
                if (global.checkAuth(request)) {
                    Context context = new Context();
                    context.setVariable("pfp", uid);

                    // Get user info
                    String displayName = "Error", username = "Error", bio = "Error fetching bio";
                    long timestamp = System.currentTimeMillis();
                    try {
                        JSONArray results = DB.execute(new SQLStatement("SELECT username, displayname, bio, timestamp FROM users WHERE id = ?", uid));
                        if (!results.isEmpty()) {
                            JSONObject result = results.getJSONObject(0);
                            displayName = result.getString("displayname");
                            username = result.getString("username");
                            bio = result.getString("bio");
                            timestamp = result.getLong("timestamp");
                        }
                    } catch (SQLException ex) {
                        ex.printStackTrace();
                    }
                    context.setVariable("display", displayName);
                    context.setVariable("username", "@" + username);
                    context.setVariable("bio", bio);
                    context.setVariable("timestamp", formatTimestamp(timestamp));
                    context.setVariable("uid", uid);
                    yield getPage("profile", model, context);
                } else yield getPage("login", model, new Context());
            }
            default -> getErrorPage(HttpStatus.NOT_FOUND, model);
        };
    }
    public record SearchResult(String link, String thumbnail, String title, String description, String uid, String author, int views, int likes, int comments, String date) {};
    public record SearchData(String query, boolean empty) {};

    // Resource pages
    @GetMapping(path = "/js/{page}", produces = "text/javascript")
    @ResponseBody 
    public ResponseEntity<String> getJs(@PathVariable String page) {
        return getRawFile(page + ".js", "text/javascript");
    }
    @GetMapping(path = "/css/{page}", produces = "text/css")
    @ResponseBody 
    public ResponseEntity<String> getCss(@PathVariable String page) {
        return getRawFile(page + ".css", "text/css");
    }
    private ResponseEntity<String> getRawFile(String fileName, String contentType) {
        String fileContents = getFileContents(fileName);
        return switch (fileContents) {
            case "404" -> ResponseEntity.status(404).contentType(MediaType.APPLICATION_JSON).body("{\"status\":\"404\",\"message\":\"The wanted file was not found.\"}");
            case "500" -> ResponseEntity.status(500).contentType(MediaType.APPLICATION_JSON).body("{\"status\":\"500\",\"message\":\"There was an problem loading the file.\"}");
            default -> ResponseEntity.ok().contentType(MediaType.valueOf(contentType)).body(fileContents);
        };
    }
    private String getFileContents(String fileName) {
        Resource file = resourceLoader.getResource("classpath:templates/" + fileName);
        if (!file.exists()) return "404";

        try {
            return StreamUtils.copyToString(file.getInputStream(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            try {
                return StreamUtils.copyToString(file.getInputStream(), StandardCharsets.UTF_8);
            } catch (IOException ex) {
                return "502";
            }
        }
    }
}
