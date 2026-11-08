package mc.jazhdo;

import java.io.IOException;
import java.nio.file.Files;
import java.sql.SQLException;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletRequest;
import mc.jazhdo.DB.SQLStatement;
import mc.jazhdo.PageManager.HomepageVideo;

@SuppressWarnings("CallToPrintStackTrace")
@RequestMapping("/api/video")
@Controller 
public class VideoAPI {
    private final Global global;

    public VideoAPI(Global global) {
        this.global = global;
    }
    
    // Helper function for ResponseEntities
    private <T> ResponseEntity<T> res(HttpStatus status) {
        return Global.res(status);
    }

    // Video endpoints
    @GetMapping(path = "/show/{video}", produces = "video/mp4")
    public ResponseEntity<byte[]> getVideo(@PathVariable String video) {
        try {
            JSONObject res = DB.executeFirst(new SQLStatement("SELECT id FROM videos WHERE id = ?", video));
            if (res != null) {
                try {
                    return ResponseEntity.ok().body(Files.readAllBytes(Global.videoPath.resolve(video)));
                } catch (IOException ex) {
                    ex.printStackTrace();
                    return res(HttpStatus.INTERNAL_SERVER_ERROR);
                }
            } else return res(HttpStatus.NOT_FOUND);
        } catch (SQLException ex) {
            ex.printStackTrace();
            return res(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    @GetMapping(path = "/homepage", produces = "application/json")
    public ResponseEntity<List<HomepageVideo>> getHomepageVideos(HttpServletRequest request) {
        try {
            JSONArray res = DB.execute(new SQLStatement("SELECT thumbnail, title, description, timestamp, id FROM videos ORDER BY rowid DESC LIMIT 50"));
            List<HomepageVideo> videos = new ArrayList<>();
            for (int i = 0; i < res.length(); i++) {
                JSONObject result = res.getJSONObject(i);
                videos.add(
                    new HomepageVideo(
                        result.getString("thumbnail"),
                        result.getString("title"),
                        result.getString("description"),
                        DateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.LONG).format(new Date(result.getLong("timestamp"))),
                        "/video/" + result.getString("id")
                    )
                );
            }
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(videos);
        } catch (SQLException ex) {
            ex.printStackTrace();
            return res(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    @PostMapping(path = "/new", produces = "text/plain")
    public ResponseEntity<String> uploadVideo(@RequestParam  MultipartFile file, @RequestParam MultipartFile thumbnail, @RequestParam String title, @RequestParam String description, HttpServletRequest request) {
        if (global.checkAuth(request)) {
            if (file == null || file.isEmpty() || thumbnail == null || thumbnail.isEmpty() || title == null || title.isEmpty() || description == null || description.isEmpty()) return ResponseEntity.status(400).body("empty");

            // Copy video into place
            String id = Global.newID();
            try {
                file.transferTo(Global.videoPath.resolve(id));
            } catch (IOException | IllegalStateException ex) {
                ex.printStackTrace();
                return ResponseEntity.status(500).body(id);
            }

            // Get thumbnail
            String thumbnailString;
            try {
                thumbnailString = "data:" + thumbnail.getContentType() + ";base64," + Base64.getEncoder().encodeToString(thumbnail.getBytes());
            } catch (IOException ex) {
                ex.printStackTrace();
                return ResponseEntity.status(500).body(id);
            }

            // Update database
            try {
                int updated = DB.executeUpdate(new SQLStatement(
                    "INSERT INTO videos (id, uid, thumbnail, title, description, views, likes, comments, shares) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    id, Global.getCookieValue("uid", request.getCookies()), thumbnailString, title, description, 0, 0, 0, 0
                ));
                if (updated == 0) return ResponseEntity.status(500).body(id);
                else return ResponseEntity.ok().body(id);
            } catch (SQLException ex) {
                ex.printStackTrace();
                return ResponseEntity.status(500).body(id);
            }
        } else return res(HttpStatus.UNAUTHORIZED);
    }
    public record VideoUpload(String fileMime, String file, String thumbnailMime, String thumbnail, String title, String description) {}
}
