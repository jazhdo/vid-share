package mc.jazhdo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.SecureRandom;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.text.DateFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import com.sksamuel.scrimage.ImmutableImage;
import com.sksamuel.scrimage.webp.WebpWriter;

import io.jsonwebtoken.Jwts;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import mc.jazhdo.PageManager.HomepageVideo;

@RequestMapping("/api")
@Controller 
public class APIPaths {
    private final Logger log = LoggerFactory.getLogger(VidShareApplication.class);
    private final Global global;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public APIPaths(Global global) {
        this.global = global;
    }

    // Internal APIs

    // Helper function for ResponseEntities
    private <T> ResponseEntity<T> res(int status) {
        return ResponseEntity.status(status).build();
    }
    private <T> ResponseEntity<T> res(HttpStatus status) {
        return res(status.value());
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

    private String uppercaseFirst(String input) {
        return input.substring(0, 1).toUpperCase() + input.substring(1);
    }

    // Auth endpoints
    @PostMapping(path = "/login", produces = "application/json")
    @ResponseBody 
    public ResponseEntity<String> verifyLogin(@RequestBody AuthRequest authRequest, HttpServletRequest request, HttpServletResponse response) {
        try (PreparedStatement ps = global.getDB().prepareStatement("SELECT id, password FROM users WHERE username = ?")) {
            ps.setString(1, authRequest.username());
            ResultSet result = ps.executeQuery();
            if (result.next()) {
                String id = result.getString("id");
                if (encoder.matches(authRequest.password(), result.getString("password"))) {
                    response.addCookie(new Cookie("auth", Jwts.builder().subject(id).issuedAt(new Date()).expiration(Date.from(new Date().toInstant().plus(Duration.ofHours(24)))).signWith(global.jwtKey).compact()));
                    response.addCookie(new Cookie("uid", id));
                    return ResponseEntity.ok().build();
                } else return res(HttpStatus.UNAUTHORIZED);
            } else return res(HttpStatus.NOT_FOUND);
        } catch (SQLException e) {
            log.warn("Checking the login for user with username {} and password {} failed. Error: {}", new Object[]{authRequest.username(), authRequest.password(), e.getMessage()});
            return res(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    public record AuthRequest(String username, String password) {}
    @PostMapping(path = "/signup", produces = "application/json")
    @ResponseBody 
    public ResponseEntity<String> createLogin(@RequestBody SignupRequest signupRequest, HttpServletRequest request, HttpServletResponse response) {
        try (PreparedStatement ps = global.getDB().prepareStatement("INSERT INTO users (id, pfp, username, password, displayname, bio, followers) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
            String uid = generateV7().toString();
            ps.setString(1, uid);
            ps.setNull(2, Types.BLOB);
            ps.setString(3, signupRequest.username());
            ps.setString(4, encoder.encode(signupRequest.password()));
            ps.setString(5, uppercaseFirst(signupRequest.username()));
            ps.setString(6, "Hello, I'm a new user!");
            ps.setInt(7, 0);
            int rows = ps.executeUpdate();
            if (rows == 0) return res(HttpStatus.UNAUTHORIZED);
            else {
                response.addCookie(new Cookie("auth", Jwts.builder().subject(uid).issuedAt(new Date()).expiration(Date.from(new Date().toInstant().plus(Duration.ofHours(24)))).signWith(global.jwtKey).compact()));
                response.addCookie(new Cookie("uid", uid));
                return ResponseEntity.ok().build();
            }
        } catch (SQLException e) {
            return res(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    public record SignupRequest(String username, String password) {}

    // Profile pic endpoints
    @GetMapping(path = "/pfp/{userid}", produces = "image/webp")
    @ResponseBody
    public ResponseEntity<byte[]> getProfilePic(@PathVariable String userid, HttpServletRequest request) {
        try (PreparedStatement ps = global.getDB().prepareStatement("SELECT pfp FROM users WHERE id = ?")) {
            ps.setString(1, userid);
            ResultSet result = ps.executeQuery();
            if (result.next()) return ResponseEntity.ok().body(result.getBytes("pfp"));
            else return res(404);
        } catch (SQLException e) {
            return res(500);
        }
    }
    @PostMapping(path = "/pfp/{userid}")
    public ResponseEntity<Void> setProfilePic(@PathVariable String userid, HttpServletRequest request) {
        if (global.checkAuth(request, userid)) {
            // Get the pfp file into .webp format
            byte[] pfp;
            try {
                pfp = ImmutableImage.loader().fromStream(request.getInputStream()).bytes(WebpWriter.MAX_LOSSLESS_COMPRESSION);
            } catch (IOException e) {
                return res(500);
            }

            // Update database
            try (PreparedStatement ps = global.getDB().prepareStatement("UPDATE users SET pfp = ? WHERE id = ?")) {
                ps.setBytes(1, pfp);
                ps.setString(2, userid);
                if (ps.executeUpdate() == 0) return res(500);
                return ResponseEntity.ok().build();
            } catch (SQLException e) {
                return res(500);
            }
        } else return res(401);
    }

    // Video endpoints
    @GetMapping(path = "/video/show/{video}", produces = "video/mp4")
    public ResponseEntity<byte[]> getVideo(@PathVariable String video, HttpServletRequest request) {
        try (PreparedStatement ps = global.getDB().prepareStatement("SELECT * FROM videos WHERE id = ?")) {
            ps.setString(1, video);
            ResultSet result = ps.executeQuery();
            if (result.next()) {
                try {
                    return ResponseEntity.ok().body(Files.readAllBytes(global.videoPath.resolve(video + ".mp4")));
                } catch (IOException e) {
                    return ResponseEntity.status(500).build();
                }
            } else return ResponseEntity.status(404).build();
        } catch (SQLException e) {
            return ResponseEntity.status(500).build();
        }
    }
    @GetMapping(path = "/video/homepage", produces = "application/json")
    public ResponseEntity<List<HomepageVideo>> getHomepageVideos(HttpServletRequest request) {
        try (PreparedStatement ps = global.getDB().prepareStatement("SELECT * FROM videos ORDER BY rowid DESC LIMIT 50")) {
            ResultSet result = ps.executeQuery();
            List<HomepageVideo> videos = new ArrayList<>();
            while (result.next()) videos.add(new HomepageVideo(result.getString("thumbnail"), result.getString("title"), result.getString("description"), DateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.LONG).format(new Date(result.getLong("timestamp"))), "/video/" + result.getString("id")));
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(videos);
        } catch (SQLException e) {
            return res(500);
        }
    }
    @PostMapping(path = "/video/new", produces = "application/json")
    public ResponseEntity<String> uploadVideo(@RequestParam MultipartFile file, @RequestParam MultipartFile thumbnail, @RequestParam String uid, @RequestParam String title, @RequestParam String description, HttpServletRequest request) {
        if (global.checkAuth(request, uid)) {
            if (file == null || file.isEmpty() || thumbnail == null || thumbnail.isEmpty()) return ResponseEntity.status(400).body("empty");
            String contentType = file.getContentType();
            if (contentType == null || !"video/mp4".equals(contentType)) return ResponseEntity.status(400).body("type");

            // Copy video into place
            String id = generateV7().toString();
            try {
                Files.copy(file.getInputStream(), global.videoPath.resolve(id + ".mp4"), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                return ResponseEntity.status(500).body(id);
            }

            // Convert thumbnail into a data URI
            String thumbnailData;
            try {
                thumbnailData = "data:" + thumbnail.getContentType() + ";base64," + Base64.getEncoder().encodeToString(thumbnail.getBytes());
            } catch (IOException e) {
                return ResponseEntity.status(500).body(id);
            }

            // Update database
            try (PreparedStatement ps = global.getDB().prepareStatement("INSERT INTO videos (id, uid, thumbnail, title, description, views, likes, comments, shares) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
                ps.setString(1, id);
                ps.setString(2, uid);
                ps.setString(3, thumbnailData);
                ps.setString(4, title);
                ps.setString(5, description);
                ps.setInt(6, 0);
                ps.setInt(7, 0);
                ps.setInt(8, 0);
                ps.setInt(9, 0);
                if (ps.executeUpdate() == 0) return ResponseEntity.status(500).body(id);
                else return ResponseEntity.ok().body(id);
            } catch (SQLException e) {
                return ResponseEntity.status(500).body(id);
            }
        } else return res(401);
    }

    // // View endpoint
    // @PostMapping(path = "/api/views/{video}")

    // // Likes endpoints
    // @PostMapping(path = "/api/likes/{video}")
    // public ResponseEntity<Void> setLike(@PathVariable String video, HttpServletRequest request) {
    //     return switch (global.checkHostAndAuth(request, request.getParameter("uid"))) {
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

}
