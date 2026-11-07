package mc.jazhdo;

import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.sksamuel.scrimage.ImmutableImage;
import com.sksamuel.scrimage.webp.WebpWriter;

import jakarta.servlet.http.HttpServletRequest;

@SuppressWarnings("CallToPrintStackTrace")
@RequestMapping("/api/pfp/{userid}")
@Controller
public class ProfilePicAPI {
    private final Global global;

    public ProfilePicAPI(Global global) {
        this.global = global;
    }

    // Helper functions
    private static <T> ResponseEntity<T> res(HttpStatus status) {
        return Global.res(status);
    }

    public ResponseEntity<byte[]> getDefaultProfilePic() {
        return ResponseEntity.ok().body(Global.defaultProfilePic);
    }
    @GetMapping(produces = {"image/webp", "image/png"})
    @ResponseBody
    public ResponseEntity<byte[]> getProfilePic(@PathVariable String userid) {
        try (PreparedStatement ps = Global.getDB().prepareStatement("SELECT pfp FROM users WHERE id = ?")) {
            ps.setString(1, userid);
            ResultSet result = ps.executeQuery();
            if (result.next()) {
                byte[] pfpBytes = result.getBytes("pfp");
                if (pfpBytes == null) return getDefaultProfilePic();
                else return ResponseEntity.ok().body(pfpBytes);
            } else return getDefaultProfilePic();
        } catch (SQLException e) {
            return getDefaultProfilePic();
        }
    }
    @PostMapping
    public ResponseEntity<Void> setProfilePic(@PathVariable String userid, HttpServletRequest request) {
        if (global.checkAuth(request, userid)) {
            // Change the pfp file into .webp format
            byte[] pfp;
            try {
                pfp = ImmutableImage.loader().fromStream(request.getInputStream()).bytes(WebpWriter.MAX_LOSSLESS_COMPRESSION);
            } catch (IOException ex) {
                ex.printStackTrace();
                return res(HttpStatus.INTERNAL_SERVER_ERROR);
            }

            // Update database
            try (PreparedStatement ps = Global.getDB().prepareStatement("UPDATE users SET pfp = ? WHERE id = ?")) {
                ps.setBytes(1, pfp);
                ps.setString(2, userid);
                if (ps.executeUpdate() == 0) return res(HttpStatus.INTERNAL_SERVER_ERROR);
                return ResponseEntity.ok().build();
            } catch (SQLException ex) {
                ex.printStackTrace();
                return res(HttpStatus.INTERNAL_SERVER_ERROR);
            }
        } else return res(HttpStatus.UNAUTHORIZED);
    }
}
