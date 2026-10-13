package mc.jazhdo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.text.DateFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import com.sksamuel.scrimage.ImmutableImage;
import com.sksamuel.scrimage.webp.WebpWriter;

import io.github.cdimascio.dotenv.Dotenv;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.HttpServletRequest;

@SuppressWarnings("CallToPrintStackTrace")
@Controller
public class PageManager {
    private final ResourceLoader resourceLoader;
    private final Logger log = LoggerFactory.getLogger(PageManager.class);
    private final ApplicationContext applicationContext;
    private final TemplateEngine templateEngine;
    private Path root, dbPath, videoPath;
    private Connection database;
    private SecretKey jwtKey;
    private String defaultProfilePic;

    public PageManager(ResourceLoader resourceLoader, ApplicationContext applicationContext, TemplateEngine templateEngine) {
        this.resourceLoader = resourceLoader;
        this.applicationContext = applicationContext;
        this.templateEngine = templateEngine;
    }

    // SQLite databases
    @EventListener(ApplicationReadyEvent.class) 
    public void onApplicationReady() {
        log.info("Starting up...");

        // Create data directory
        root = Paths.get("").toAbsolutePath();
        dbPath = root.resolve("database");
        videoPath = root.resolve("videos");
        try {
            Files.createDirectories(dbPath);
            Files.createDirectories(videoPath);
        } catch (IOException e) {
            e.printStackTrace();
            log.error("Creating either the database or the video directory failed. Error: {}", e.getMessage());
            return;
        }

        // Get secure env values
        Dotenv envValues = Dotenv.configure().directory(root.toAbsolutePath().toString()).filename(".local.env").load();
        defaultProfilePic = envValues.get("PFP");
        
        // Get JWT key for use in login
        String stringjwtkey = envValues.get("JWT_KEY");
        if (stringjwtkey == null) {
            log.error("Invalid JWT key in .local.env file. This application will break without this.");
            SpringApplication.exit(applicationContext, () -> 404);
        } else jwtKey = Keys.hmacShaKeyFor(stringjwtkey.getBytes(StandardCharsets.UTF_8));

        // Users database
        try {
            database = DriverManager.getConnection("jdbc:sqlite:database/database.db");
        } catch (SQLException e) {
            e.printStackTrace();
            log.error("Getting connections to the database failed. Error: {}", e.getMessage());
            return;
        }

        // Database table
        try (Statement s = database.createStatement()) {
            // User tables
            s.execute("CREATE TABLE IF NOT EXISTS users (id TEXT NOT NULL UNIQUE, pfp BLOB, username TEXT NOT NULL UNIQUE COLLATE NOCASE, password TEXT NOT NULL, displayname TEXT COLLATE NOCASE, bio TEXT, followers INTEGER NOT NULL, timestamp INTEGER NOT NULL DEFAULT (CAST(unixepoch('subsec') * 1000 AS INTEGER)), PRIMARY KEY(id))");
            s.execute("CREATE TABLE IF NOT EXISTS followers (uid TEXT NOT NULL, followed TEXT NOT NULL COLLATE NOCASE, timestamp INTEGER NOT NULL DEFAULT (CAST(unixepoch('subsec') * 1000 AS INTEGER)))");
           
            // Video tables
            s.execute("CREATE TABLE IF NOT EXISTS videos (id TEXT NOT NULL UNIQUE, uid TEXT NOT NULL, thumbnail TEXT NOT NULL, title TEXT NOT NULL COLLATE NOCASE, description TEXT NOT NULL COLLATE NOCASE, views INTEGER NOT NULL, likes INTEGER NOT NULL, comments INTEGER NOT NULL, shares INTEGER NOT NULL, timestamp INTEGER NOT NULL DEFAULT (CAST(unixepoch('subsec') * 1000 AS INTEGER)), PRIMARY KEY(id))");
            s.execute("CREATE VIRTUAL TABLE IF NOT EXISTS videos_search USING fts5(title, description)");
            s.execute("CREATE TABLE IF NOT EXISTS views (uid TEXT NOT NULL, video TEXT NOT NULL COLLATE NOCASE, timestamp INTEGER NOT NULL DEFAULT (CAST(unixepoch('subsec') * 1000 AS INTEGER)))");
            s.execute("CREATE TABLE IF NOT EXISTS likes (uid TEXT NOT NULL, video TEXT NOT NULL COLLATE NOCASE, timestamp INTEGER NOT NULL DEFAULT (CAST(unixepoch('subsec') * 1000 AS INTEGER)))");
            s.execute("CREATE TABLE IF NOT EXISTS comments (id TEXT NOT NULL UNIQUE, uid TEXT NOT NULL, likes INTEGER NOT NULL, video TEXT NOT NULL COLLATE NOCASE, parent TEXT NOT NULL COLLATE NOCASE, timestamp INTEGER NOT NULL DEFAULT (CAST(unixepoch('subsec') * 1000 AS INTEGER)), PRIMARY KEY(id))");
            s.execute("CREATE TABLE IF NOT EXISTS shares (uid TEXT NOT NULL, video TEXT NOT NULL COLLATE NOCASE, timestamp INTEGER NOT NULL DEFAULT (CAST(unixepoch('subsec') * 1000 AS INTEGER)))");
            s.execute("DELETE FROM videos_search");
        } catch (SQLException e) {
            e.printStackTrace();
            log.error("Creating the database tables failed. Error: {}", e.getMessage());
            return;
        }
        // Populate index
        try (PreparedStatement ps = database.prepareStatement("INSERT INTO videos_search(rowid, title, description) SELECT rowid, title, description FROM videos")) {
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
            log.error("Populating the video search index table failed. Error: {}", e.getMessage());
        }
    }

    // Helper functions for page paths
    private boolean checkHost(HttpServletRequest request) {
        String remoteHost = request.getRemoteHost();
        log.info("{} request recieved from {} to {}.", new Object[]{request.getMethod(), remoteHost, request.getRequestURI()});
        return "video.itsjaz.com".equals(remoteHost) || "127.0.0.1".equals(remoteHost);
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
        try (PreparedStatement ps = database.prepareStatement("SELECT * FROM videos ORDER BY rowid DESC LIMIT 50")) {
            ResultSet result = ps.executeQuery();
            List<HomepageVideo> videos = new ArrayList<>();
            while (result.next()) videos.add(new HomepageVideo(result.getString("thumbnail"), result.getString("title"), result.getString("description"), DateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.LONG).format(new Date(result.getLong("timestamp"))), "/watch?v=" + result.getString("id")));
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
                        try (PreparedStatement ps = database.prepareStatement("SELECT v.id, v.thumbnail, v.title, v.description, v.views, v.likes, v.shares, v.timestamp, u.pfp, u.displayname FROM videos v JOIN videos_search s ON v.rowid = s.rowid JOIN users u ON v.uid = u.id WHERE videos_search MATCH ? ORDER BY s.rank ASC")) {
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

    // Internal APIs

    // Helper function for ResponseEntities
    private <T> ResponseEntity<T> res(int status) {
        return ResponseEntity.status(status).build();
    }

    // Gemini generated user id generator
    private static final SecureRandom random = new SecureRandom();
    public static UUID generateV7() {
        long msb = (System.currentTimeMillis() & 0xFFFFFFFFFFFFL) << 16;
        msb |= random.nextLong() & 0xFFFL;
        msb = (msb & 0xFFFFFFFFFFFF0FFFL) | (0x7L << 12);
        long lsb = random.nextLong();
        lsb = (lsb & 0x3FFFFFFFFFFFFFFFL) | 0x8000000000000000L;
        return new UUID(msb, lsb);
    }

    // Auth endpoints
    @GetMapping(path = "/api/login", produces = "application/json")
    @ResponseBody 
    public ResponseEntity<AuthResponse> verifyLogin(@RequestBody AuthRequest authRequest, HttpServletRequest request) {
        if (checkHost(request)) {
            try (PreparedStatement ps = database.prepareStatement("SELECT id FROM users WHERE username = ? AND password = ?")) {
                ps.setString(1, authRequest.username());
                ps.setString(2, (new BCryptPasswordEncoder()).encode(authRequest.password()));
                ResultSet result = ps.executeQuery();
                if (result.next()) {
                    String id = result.getString("id");
                    return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(new AuthResponse(id, Jwts.builder().subject(id).issuedAt(new Date()).expiration(Date.from(new Date().toInstant().plus(Duration.ofHours(24)))).signWith(jwtKey).compact()));
                } else return ResponseEntity.status(401).build();
            } catch (SQLException e) {
                log.warn("Checking the login for user with username {} and password {} failed. Error: {}", new Object[]{authRequest.username(), authRequest.password(), e.getMessage()});
                return ResponseEntity.status(500).build();
            }
        } else return ResponseEntity.status(421).build();
    }
    public record AuthRequest(String username, String password) {}
    public record AuthResponse(String uid, String token) {}
    @PostMapping(path = "/api/signup", produces = "application/json")
    @ResponseBody 
    public ResponseEntity<AuthResponse> createLogin(@RequestBody SignupRequest signupRequest, HttpServletRequest request) {
        if (checkHost(request)) {
            try (PreparedStatement ps = database.prepareStatement("INSERT INTO users (id, pfp, username, password, displayname, bio, followers) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                String uid = generateV7().toString();
                ps.setString(1, uid);
                ps.setNull(2, Types.BLOB);
                ps.setString(3, signupRequest.username());
                ps.setString(4, new BCryptPasswordEncoder().encode(signupRequest.password()));
                ps.setString(5, uppercaseFirst(signupRequest.username()));
                ps.setString(6, "Hello, I'm a new user!");
                ps.setInt(7, 0);
                int rows = ps.executeUpdate();
                if (rows == 0) return res(500);
                else return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(new AuthResponse(uid, Jwts.builder().subject(uid).issuedAt(new Date()).expiration(Date.from(new Date().toInstant().plus(Duration.ofHours(24)))).signWith(jwtKey).compact()));
            } catch (SQLException e) {
                return res(500);
            }
        } else return res(421);
    }
    public record SignupRequest(String username, String password) {}

    // Auth tools
    private boolean checkAuth(HttpServletRequest request, String userID) {
        // Check for the existence of the header
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) return false;
        
        // Get token payload
        Claims payload;
        try {
            payload = Jwts.parser().verifyWith(jwtKey).build().parseSignedClaims(authHeader.substring(7)).getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }

        // Check that the token is for this user and return true for right auth
        return payload.getSubject().equals(userID);
    }

    // Profile pic endpoints
    @GetMapping(path = "/api/pfp/{userid}", produces = "image/webp")
    @ResponseBody
    public ResponseEntity<byte[]> getProfilePic(@PathVariable String userid, HttpServletRequest request) {
        if (checkHost(request)) {
            try (PreparedStatement ps = database.prepareStatement("SELECT pfp FROM users WHERE id = ?")) {
                ps.setString(1, userid);
                ResultSet result = ps.executeQuery();
                if (result.next()) return ResponseEntity.ok().body(result.getBytes("pfp"));
                else return ResponseEntity.status(404).build();
            } catch (SQLException e) {
                return ResponseEntity.status(500).build();
            }
        } else return ResponseEntity.status(421).build();
    }
    @PostMapping(path = "/api/pfp/{userid}")
    public ResponseEntity<Void> setProfilePic(@PathVariable String userid, HttpServletRequest request) {
        if (checkHost(request)) {
            if (checkAuth(request, userid)) {
                // Get the pfp file into .webp format
                byte[] pfp;
                try {
                    pfp = ImmutableImage.loader().fromStream(request.getInputStream()).bytes(WebpWriter.MAX_LOSSLESS_COMPRESSION);
                } catch (IOException e) {
                    return ResponseEntity.status(500).build();
                }

                // Update database
                try (PreparedStatement ps = database.prepareStatement("UPDATE users SET pfp = ? WHERE id = ?")) {
                    ps.setBytes(1, pfp);
                    ps.setString(2, userid);
                    if (ps.executeUpdate() == 0) return ResponseEntity.status(500).build();
                    return ResponseEntity.ok().build();
                } catch (SQLException e) {
                    return ResponseEntity.status(500).build();
                }
            } else return ResponseEntity.status(401).build();
        } else return ResponseEntity.status(421).build();
    }

    // Video endpoints
    @GetMapping(path = "/api/video/{video}", produces = "video/mp4")
    public ResponseEntity<byte[]> getVideo(@PathVariable String video, HttpServletRequest request) {
        if (checkHost(request)) {
            try (PreparedStatement ps = database.prepareStatement("SELECT * FROM videos WHERE id = ?")) {
                ps.setString(1, video);
                ResultSet result = ps.executeQuery();
                if (result.next()) {
                    try {
                        return ResponseEntity.ok().body(Files.readAllBytes(videoPath.resolve(video + ".mp4")));
                    } catch (IOException e) {
                        return ResponseEntity.status(500).build();
                    }
                } else return ResponseEntity.status(404).build();
            } catch (SQLException e) {
                return ResponseEntity.status(500).build();
            }
        } else return res(421);
    }
    @GetMapping(path = "/api/homepage-video", produces = "application/json")
    public ResponseEntity<List<HomepageVideo>> getHomepageVideos(HttpServletRequest request) {
        if (checkHost(request)) {
            try (PreparedStatement ps = database.prepareStatement("SELECT * FROM videos ORDER BY rowid DESC LIMIT 50")) {
                ResultSet result = ps.executeQuery();
                List<HomepageVideo> videos = new ArrayList<>();
                while (result.next()) videos.add(new HomepageVideo(result.getString("thumbnail"), result.getString("title"), result.getString("description"), DateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.LONG).format(new Date(result.getLong("timestamp"))), "/video/" + result.getString("id")));
                return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(videos);
            } catch (SQLException e) {
                return res(500);
            }
        } else return res(421);
    }
    @PostMapping(path = "/api/new-video", produces = "application/json")
    public ResponseEntity<String> uploadVideo(@RequestParam MultipartFile file, @RequestParam String uid, @RequestParam String title, @RequestParam String description, HttpServletRequest request) {
        if (checkHost(request)) {
            if (checkAuth(request, uid)) {
                if (file == null || file.isEmpty()) return ResponseEntity.status(400).body("empty");
                String contentType = file.getContentType();
                if (contentType == null || !"video/mp4".equals(contentType)) return ResponseEntity.status(400).body("type");

                // Copy video into place
                String id = generateV7().toString();
                try {
                    Files.copy(file.getInputStream(), videoPath.resolve(videoPath.resolve(id + ".mp4")), StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    return ResponseEntity.status(500).body(id);
                }

                // Update database
                try (PreparedStatement ps = database.prepareStatement("INSERT INTO videos (id, uid, thumbnail, title, description, views, likes, comments, shares) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
                    ps.setString(1, id);
                    ps.setString(2, uid);
                    ps.setString(3, title);
                    ps.setString(4, description);
                    ps.setInt(5, 0);
                    ps.setInt(6, 0);
                    ps.setInt(7, 0);
                    ps.setInt(8, 0);
                    if (ps.executeUpdate() == 0) return ResponseEntity.status(500).body(id);
                    else return ResponseEntity.ok().body(id);
                } catch (SQLException e) {
                    return ResponseEntity.status(500).body(id);
                }
            } else return res(401);
        } else return res(421);
    }

    // // View endpoint
    // @PostMapping(path = "/api/views/{video}")

    // // Likes endpoints
    // @PostMapping(path = "/api/likes/{video}")
    // public ResponseEntity<Void> setLike(@PathVariable String video, HttpServletRequest request) {
    //     return switch (checkHostAndAuth(request, request.getParameter("uid"))) {
    //         case 0 -> {
    //             boolean toLike = request.getParameter("like").equals("true");

    //             yield ResponseEntity.ok().build();
    //         }
    //         case -1 -> ResponseEntity.status(401).build();
    //         case -2 -> ResponseEntity.status(421).build();
    //         default -> ResponseEntity.status(500).build();
    //     };
    // }

    // // Shares endpoints

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
