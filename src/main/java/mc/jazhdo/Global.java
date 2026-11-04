package mc.jazhdo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
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
    public static final Path root = Paths.get(""), dbPath = root.resolve("database.db"), videoPath = root.resolve("videos");
    private static final Logger log = LoggerFactory.getLogger(VidShareApplication.class);
    private final ApplicationContext applicationContext;
    public String defaultProfilePic;
    public SecretKey jwtKey;

    public Global(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    // SQLite databases
    @EventListener(ApplicationReadyEvent.class) 
    public void onApplicationReady() {
        log.info("Starting up...");

        // Get environment values
        defaultProfilePic = getSafeEnvironmentVar("DEFAULT_PFP");
        jwtKey = Keys.hmacShaKeyFor(getSafeEnvironmentVar("JWT_KEY").getBytes(StandardCharsets.UTF_8));

        // Create folders
        try {
            Files.createDirectories(videoPath);
        } catch (IOException ex) {
            ex.printStackTrace();
            log.error("Creating the video directory failed. Error: {}", ex.getMessage());
            return;
        }

        // Database tables
        try (Statement s = getDB().createStatement()) {
            // User tables
            s.execute("CREATE TABLE IF NOT EXISTS users (id TEXT NOT NULL UNIQUE, pfp BLOB, username TEXT NOT NULL UNIQUE COLLATE NOCASE, password TEXT NOT NULL, displayname TEXT COLLATE NOCASE, bio TEXT, followers INTEGER NOT NULL, timestamp INTEGER NOT NULL DEFAULT (CAST(unixepoch('subsec') * 1000 AS INTEGER)), PRIMARY KEY(id))");
            s.execute("CREATE TABLE IF NOT EXISTS followers (uid TEXT NOT NULL, followed TEXT NOT NULL COLLATE NOCASE, timestamp INTEGER NOT NULL DEFAULT (CAST(unixepoch('subsec') * 1000 AS INTEGER)))");

            // Video tables
            s.execute("CREATE TABLE IF NOT EXISTS videos (id TEXT NOT NULL UNIQUE, uid TEXT NOT NULL, thumbnail TEXT NOT NULL, title TEXT NOT NULL COLLATE NOCASE, description TEXT NOT NULL COLLATE NOCASE, views INTEGER NOT NULL, likes INTEGER NOT NULL, comments INTEGER NOT NULL, shares INTEGER NOT NULL, timestamp INTEGER NOT NULL DEFAULT (CAST(unixepoch('subsec') * 1000 AS INTEGER)), PRIMARY KEY(id))");
            s.execute("CREATE VIRTUAL TABLE IF NOT EXISTS videos_search USING fts5(title, description)");
            s.execute("CREATE TABLE IF NOT EXISTS views (uid TEXT NOT NULL, video TEXT NOT NULL COLLATE NOCASE, timestamp INTEGER NOT NULL DEFAULT (CAST(unixepoch('subsec') * 1000 AS INTEGER)))");
            s.execute("CREATE TABLE IF NOT EXISTS likes (uid TEXT NOT NULL, video TEXT NOT NULL COLLATE NOCASE, timestamp INTEGER NOT NULL DEFAULT (CAST(unixepoch('subsec') * 1000 AS INTEGER)))");
            s.execute("CREATE TABLE IF NOT EXISTS comments (id TEXT NOT NULL UNIQUE, uid TEXT NOT NULL, likes INTEGER NOT NULL, video TEXT NOT NULL COLLATE NOCASE, parent TEXT COLLATE NOCASE, timestamp INTEGER NOT NULL DEFAULT (CAST(unixepoch('subsec') * 1000 AS INTEGER)), PRIMARY KEY(id))");
            s.execute("CREATE TABLE IF NOT EXISTS shares (uid TEXT NOT NULL, video TEXT NOT NULL COLLATE NOCASE, timestamp INTEGER NOT NULL DEFAULT (CAST(unixepoch('subsec') * 1000 AS INTEGER)))");
            s.execute("DELETE FROM videos_search");
        } catch (SQLException ex) {
            ex.printStackTrace();
            log.error("Creating the database tables failed. Error: {}", ex.getMessage());
            return;
        }

        // Populate search index
        try (PreparedStatement ps = getDB().prepareStatement("INSERT INTO videos_search(rowid, title, description) SELECT rowid, title, description FROM videos")) {
            ps.executeUpdate();
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

    // Auth tools
    public static String getCookieValue(String key, Cookie[] cookies) {
        // Null check
        if (cookies == null) return null;
        return Arrays.stream(cookies).filter(cookie -> cookie.getName().equals(key)).findFirst().map(Cookie::getValue).orElse(null);
    }
    public boolean checkAuth(HttpServletRequest request) {
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
        return payload.getSubject().equals(UID);
    }
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
        return verifyUID.equals(UID) && payload.getSubject().equals(UID);
    }

    // Database retriever
    public static Connection getDB() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + dbPath);
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
