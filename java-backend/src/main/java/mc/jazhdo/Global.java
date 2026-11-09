package mc.jazhdo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

@SuppressWarnings("CallToPrintStackTrace")
@Component 
public class Global {
    public static final Path root = Paths.get(""), dbPath = root.resolve(getEnvOrElse("DB_PATH", "database.db")), videoPath = root.resolve(getEnvOrElse("VIDEO_PATH", "videos"));
    public static final byte[] defaultProfilePic;
    public static final Logger log = LoggerFactory.getLogger(VidShareApplication.class);
    private static final ClassLoader classLoader = VidShareApplication.class.getClassLoader();
    private final ApplicationContext applicationContext;
    public SecretKey jwtKey;

    /**
     * Gets a system environment variable or returns a default value if its null
     * 
     * @param name The name of the system environment variable
     * @param otherwise The value to default to
     * @return The value of the environment variable, or the defaulted to value
     */
    private static String getEnvOrElse(String name, String otherwise) {
        String env = System.getenv(name);
        return env == null ? otherwise : env;
    }

    static {
        // Create folders
        try {
            Files.createDirectories(videoPath);
        } catch (IOException ex) {
            ex.printStackTrace();
            log.error("Creating the video directory failed. Error: {}", ex.getMessage());
        }

        // Get default PFP
        byte[] tempPFP;
        try {
            Path file = root.resolve(getEnvOrElse("DEFAULT_PFP", "cat.png"));
            if (Files.exists(file)) tempPFP = Files.readAllBytes(file);
            else tempPFP = classLoader.getResourceAsStream("cat.png").readAllBytes();
        } catch (IOException ex) {
            ex.printStackTrace();
            log.error("Getting the default PFP's bytes failed. Error: {}", ex.getMessage());
            tempPFP = new byte[0];
        }
        defaultProfilePic = tempPFP;
    }

    public Global(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    // Startup
    @EventListener(ApplicationReadyEvent.class) 
    public void onApplicationReady() {
        log.info("Starting up...");

        // Get environment values
        jwtKey = Keys.hmacShaKeyFor(getSafeEnvironmentVar("JWT_KEY").getBytes(StandardCharsets.UTF_8));

        // Database tables
        try {
            DB.execute(
                // User tables
                "CREATE TABLE IF NOT EXISTS users (id TEXT NOT NULL UNIQUE, pfp BLOB, username TEXT NOT NULL UNIQUE COLLATE NOCASE, password TEXT NOT NULL, displayname TEXT COLLATE NOCASE, bio TEXT, followers INTEGER NOT NULL, timestamp INTEGER NOT NULL DEFAULT (CAST(unixepoch('subsec') * 1000 AS INTEGER)), PRIMARY KEY(id))",
                "CREATE TABLE IF NOT EXISTS followers (uid TEXT NOT NULL, followed TEXT NOT NULL COLLATE NOCASE, timestamp INTEGER NOT NULL DEFAULT (CAST(unixepoch('subsec') * 1000 AS INTEGER)))",

                // Video tables
                "CREATE TABLE IF NOT EXISTS videos (id TEXT NOT NULL UNIQUE, uid TEXT NOT NULL, thumbnail TEXT NOT NULL, title TEXT NOT NULL COLLATE NOCASE, description TEXT NOT NULL COLLATE NOCASE, views INTEGER NOT NULL, likes INTEGER NOT NULL, comments INTEGER NOT NULL, shares INTEGER NOT NULL, timestamp INTEGER NOT NULL DEFAULT (CAST(unixepoch('subsec') * 1000 AS INTEGER)), PRIMARY KEY(id))",
                "CREATE VIRTUAL TABLE IF NOT EXISTS videos_search USING fts5(title, description)",
                "CREATE TABLE IF NOT EXISTS views (uid TEXT NOT NULL, video TEXT NOT NULL COLLATE NOCASE, timestamp INTEGER NOT NULL DEFAULT (CAST(unixepoch('subsec') * 1000 AS INTEGER)))",
                "CREATE TABLE IF NOT EXISTS likes (uid TEXT NOT NULL, video TEXT NOT NULL COLLATE NOCASE, timestamp INTEGER NOT NULL DEFAULT (CAST(unixepoch('subsec') * 1000 AS INTEGER)))",
                "CREATE TABLE IF NOT EXISTS comments (id TEXT NOT NULL UNIQUE, uid TEXT NOT NULL, likes INTEGER NOT NULL, video TEXT NOT NULL COLLATE NOCASE, parent TEXT COLLATE NOCASE, timestamp INTEGER NOT NULL DEFAULT (CAST(unixepoch('subsec') * 1000 AS INTEGER)), PRIMARY KEY(id))",
                "CREATE TABLE IF NOT EXISTS shares (uid TEXT NOT NULL, video TEXT NOT NULL COLLATE NOCASE, timestamp INTEGER NOT NULL DEFAULT (CAST(unixepoch('subsec') * 1000 AS INTEGER)))",
                "DELETE FROM videos_search"
            );
        } catch (SQLException ex) {
            ex.printStackTrace();
            log.error("Creating the database tables failed. Error: {}", ex.getMessage());
            return;
        }

        // Populate search index
        try {
            DB.executeUpdate("INSERT INTO videos_search(rowid, title, description) SELECT rowid, title, description FROM videos");
        } catch (SQLException ex) {
            ex.printStackTrace();
            log.error("Populating the video search index table failed. Error: {}", ex.getMessage());
        }
    }
    private String getSafeEnvironmentVar(String name) {
        String env = System.getenv(name);
        if (env == null) {
            log.error("Invalid " + name + " environment variable. Shutting down for safety reasons..");
            System.exit(SpringApplication.exit(applicationContext, () -> 404));
        }
        return env;
    }

    /**
     * Gets the value of a cookie given its key from a array of cookies
     * 
     * @param key The key to search for
     * @param cookies The array of cookies to look in
     * @return The value of the cookie, or null if not found
     */
    public static String getCookieValue(String key, Cookie[] cookies) {
        // Null check
        if (cookies == null) return null;
        return Arrays.stream(cookies).filter(cookie -> cookie.getName().equals(key)).findFirst().map(cookie -> cookie.getValue()).orElse(null);
    }

    public boolean checkAuth(HttpServletRequest request) {
        return checkAuth(request, null);
    }
    /**
     * Checks the auth state of a request
     * 
     * @param request The request to check for
     * @param verifyUID The UID to check (or null to not check)
     * @return Whether or not the request is logged in
     */
    public boolean checkAuth(HttpServletRequest request, String verifyUID) {
        // Check for the existence of the header
        Cookie[] cookies = request.getCookies();
        String UID = getCookieValue("uid", cookies), token = getCookieValue("token", cookies);
        if (UID == null || token == null) return false;

        // Get token payload
        Claims payload;
        try {
            payload = Jwts.parser().verifyWith(jwtKey).build().parseSignedClaims(token).getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }

        // Check that the token is for this user and return true for right auth
        return (verifyUID == null || verifyUID.equals(UID)) && payload.getSubject().equals(UID);
    }

    // Gemini generated id generator
    private static final SecureRandom random = new SecureRandom();
    public static String newID() {
        long msb = (System.currentTimeMillis() & 0xFFFFFFFFFFFFL) << 16;
        msb |= random.nextLong() & 0xFFFL;
        msb = (msb & 0xFFFFFFFFFFFF0FFFL) | (0x7L << 12);
        long lsb = random.nextLong();
        lsb = (lsb & 0x3FFFFFFFFFFFFFFFL) | 0x8000000000000000L;
        return new UUID(msb, lsb).toString();
    }

    // Helper for ResponseEntity
    public static <T> ResponseEntity<T> res(HttpStatus status) {
        return ResponseEntity.status(status).build();
    }
}
