package mc.jazhdo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
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

@SuppressWarnings("CallToPrintStackTrace")
@RequestMapping("")
@Controller
public class PageManager {
    private final ResourceLoader resourceLoader;
    private final Logger log = LoggerFactory.getLogger(VidShareApplication.class);
    private final TemplateEngine templateEngine;
    private final Global global;
    private String defaultProfilePic;

    public PageManager(Global global, ResourceLoader resourceLoader, TemplateEngine templateEngine) {
        this.global = global;
        this.resourceLoader = resourceLoader;
        this.templateEngine = templateEngine;
    }

    // Helper functions for page paths

    private boolean checkHost(HttpServletRequest request) {
        return global.checkHost(request);
    }
    private String uppercaseFirst(String input) {
        return input.substring(0, 1).toUpperCase() + input.substring(1);
    }
    private String getPage(String pageName, Model model) {
        return getPage(pageName, model, new Context());
    }
    private String getPage(String pageName, Model model, Context pageContent) {
        String fileContents = templateEngine.process(pageName + ".html", pageContent);
        Page page = new Page(uppercaseFirst(pageName), fileContents);
        model.addAttribute("page", page);
        model.addAttribute("pfp", defaultProfilePic);
        return "main";
    }
    public record Page(String name, String content) {};
    private String getErrorPage(int errorCode, Model model) {
        Context context = new Context();
        context.setVariable("header", "Error " + Integer.toString(errorCode) + " " + switch (errorCode) {
            case 400 -> "Bad Request";
            case 404 -> "Not Found";
            case 421 -> "Misdirected Request";
            case 500 -> "Internal Server Error";
            default -> "Unknown Error";
        });
        return getPage("error", model, context);
    }

    // Paths
    @GetMapping(path = "/", produces = "text/html")
    public String getSlash(Model model, HttpServletRequest request) {
        return checkHost(request) ? getHome(model, request) : getErrorPage(421, model);
    }
    public String getHome(Model model, HttpServletRequest request) {
        Context context = new Context();
        try (PreparedStatement ps = global.getDB().prepareStatement("SELECT * FROM videos ORDER BY rowid DESC LIMIT 50")) {
            ResultSet result = ps.executeQuery();
            List<HomepageVideo> videos = new ArrayList<>();
            while (result.next()) videos.add(new HomepageVideo(result.getString("thumbnail"), result.getString("title"), result.getString("description"), "Uploaded " + DateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.LONG).format(new Date(result.getLong("timestamp"))), "/watch?v=" + result.getString("id")));
            context.setVariable("videos", videos);
        } catch (SQLException e) {
            return getErrorPage(500, model);
        }
        return getPage("home", model, context);
    }
    public record HomepageVideo(String thumbnail, String title, String description, String date, String link) {}
    @GetMapping(path = "/{page}", produces = "text/html")
    public String getNormalPage(@PathVariable String page, Model model, HttpServletRequest request) {
        if (checkHost(request)) {
            return switch (page) {
                case "home" -> getHome(model, request);
                case "search" -> {
                    String query = request.getParameter("q");
                    if (query != null) {
                        List<SearchResult> searchResults = new ArrayList<>();
                        try (PreparedStatement ps = global.getDB().prepareStatement("SELECT v.id, v.thumbnail, v.title, v.description, v.views, v.likes, v.shares, v.timestamp, u.pfp, u.displayname FROM videos v JOIN videos_search s ON v.rowid = s.rowid JOIN users u ON v.uid = u.id WHERE videos_search MATCH ? ORDER BY s.rank ASC")) {
                            ps.setString(1, query);
                            ResultSet results = ps.executeQuery();
                            while (results.next()) searchResults.add(new SearchResult("/watch?v=" + results.getString("id"), results.getString("thumbnail"), results.getString("title"), results.getString("description"), results.getBytes("pfp"), results.getString("displayname"), results.getInt("views"), results.getInt("likes"), 0, DateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.LONG).format(new Date(results.getLong("timestamp")))));
                        } catch (SQLException e) {
                            log.warn("Search Error: {}", e.getMessage());
                            yield getErrorPage(500, model);
                        }
                        Context context = new Context();
                        context.setVariable("results", searchResults);
                        context.setVariable("search", new SearchData(query, searchResults.isEmpty()));
                        yield getPage("search", model, context);
                    } else yield getErrorPage(400, model);
                }
                case "make" -> getPage(page, model);
                case "watch" -> {
                    Context context = new Context();
                    String videoID = request.getParameter("v");
                    log.info("VID ID: {}", videoID);
                    context.setVariable("id", videoID);
                    yield getPage("watch", model, context);
                }
                default -> getErrorPage(404, model);
            };
        } else return getErrorPage(421, model);
    }
    public record SearchResult(String link, String thumbnail, String title, String description, byte[] pfp, String author, int views, int likes, int comments, String date) {};
    public record SearchData(String query, boolean empty) {};

    // Resource pages
    @GetMapping(path = "/js/{page}", produces = "text/javascript")
    @ResponseBody 
    public ResponseEntity<String> getJs(@PathVariable String path) {
        return getRawFile(path + ".js");
    }
    @GetMapping(path = "/css/{page}", produces = "text/css")
    @ResponseBody 
    public ResponseEntity<String> getCss(@PathVariable String path) {
        return getRawFile(path + ".css");
    }
    private ResponseEntity<String> getRawFile(String fileName) {
        String fileContents = getFileContents(fileName);
        return switch (fileContents) {
            case "404" -> ResponseEntity.status(404).contentType(MediaType.APPLICATION_JSON).body("{\"status\":\"404\",\"message\":\"The wanted file was not found.\"}");
            case "500" -> ResponseEntity.status(500).contentType(MediaType.APPLICATION_JSON).body("{\"status\":\"500\",\"message\":\"There was an problem loading the file.\"}");
            default -> ResponseEntity.ok().body(fileContents);
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
