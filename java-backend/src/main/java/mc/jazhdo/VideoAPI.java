package mc.jazhdo;

import java.io.IOException;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.sql.SQLException;
import java.util.Base64;

import org.json.JSONObject;
import org.springframework.http.HttpStatus;
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
                    return ResponseEntity.ok().body(DB.get("vid", BodyHandlers.ofByteArray(), "File-Id", video).body());
                } catch (SQLException ex) {
                    ex.printStackTrace();
                    return res(HttpStatus.INTERNAL_SERVER_ERROR);
                }
            } else return res(HttpStatus.NOT_FOUND);
        } catch (SQLException ex) {
            ex.printStackTrace();
            return res(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    @PostMapping(path = "/new", produces = "text/plain")
    public ResponseEntity<String> uploadVideo(@RequestParam MultipartFile file, @RequestParam MultipartFile thumbnail, @RequestParam String title, @RequestParam String description, HttpServletRequest request) {
        if (global.checkAuth(request)) {
            if (file == null || file.isEmpty() || thumbnail == null || thumbnail.isEmpty() || title == null || title.isEmpty() || description == null || description.isEmpty()) return ResponseEntity.status(400).body("empty");

            // Copy video into place
            String id = Global.newID();
            try {
                DB.post("vid", BodyPublishers.ofByteArray(file.getBytes()), BodyHandlers.ofString(), "Content-Type", file.getContentType(), "File-Id", id);
            } catch (SQLException | IOException ex) {
                ex.printStackTrace();
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(id);
            }

            // Get thumbnail
            String thumbnailString;
            try {
                thumbnailString = "data:" + thumbnail.getContentType() + ";base64," + Base64.getEncoder().encodeToString(thumbnail.getBytes());
            } catch (IOException ex) {
                ex.printStackTrace();
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(id);
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
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(id);
            }
        } else return res(HttpStatus.UNAUTHORIZED);
    }
}
