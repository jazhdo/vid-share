package mc.jazhdo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

import javax.crypto.SecretKey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import io.github.cdimascio.dotenv.Dotenv;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.HttpServletRequest;

@SuppressWarnings("CallToPrintStackTrace")
@Component 
public class Global {
    private final Logger log = LoggerFactory.getLogger(VidShareApplication.class);
    private final ApplicationContext applicationContext;
    public Path root, dbPath, videoPath;
    private String defaultProfilePic;
    public Connection database;
    public SecretKey jwtKey;

    public Global(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    // SQLite databases
    @EventListener(ApplicationReadyEvent.class) 
    public void onApplicationReady() {
        log.info("Starting up...");

        // Get secure env values
        root = Paths.get("");
        Dotenv envValues = Dotenv.configure().directory(root.toAbsolutePath().toString()).filename(".local.env").load();
        defaultProfilePic = envValues.get("PFP");
        
        // Get JWT key for use in login
        String stringjwtkey = envValues.get("JWT_KEY");
        if (stringjwtkey == null) {
            log.error("Invalid JWT key in .local.env file. This application will break without this.");
            SpringApplication.exit(applicationContext, () -> 404);
            return;
        } else jwtKey = Keys.hmacShaKeyFor(stringjwtkey.getBytes(StandardCharsets.UTF_8));

        // Create folders
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
        dbPath = dbPath.resolve("database.db");

        // Database
        try {
            database = getDB();
        } catch (SQLException e) {
            e.printStackTrace();
            log.error("Getting connections to the database failed. Error: {}", e.getMessage());
            return;
        }

        // Database tables
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

        // Populate search index
        try (PreparedStatement ps = database.prepareStatement("INSERT INTO videos_search(rowid, title, description) SELECT rowid, title, description FROM videos")) {
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
            log.error("Populating the video search index table failed. Error: {}", e.getMessage());
        }
    }

    // Auth tools
    public boolean checkAuth(HttpServletRequest request, String userID) {
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

    public boolean checkHost(HttpServletRequest request) {
        String remoteHost = request.getRemoteHost();
        log.info("{} request recieved from {} to {}.", new Object[]{request.getMethod(), remoteHost, request.getRequestURI()});
        return "video.itsjaz.com".equals(remoteHost) || "127.0.0.1".equals(remoteHost) || "0:0:0:0:0:0:0:1".equals(remoteHost);
    }

    public Connection getDB() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + dbPath);
    }
}
