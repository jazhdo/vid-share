package mc.jazhdo;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Duration;
import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import io.jsonwebtoken.Jwts;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@SuppressWarnings("CallToPrintStackTrace")
@RequestMapping("/api")
@Controller 
public class APIPaths {
    private final Logger log = LoggerFactory.getLogger(VidShareApplication.class);
    private final Global global;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public APIPaths(Global global) {
        this.global = global;
    }

    // Helper function for ResponseEntities
    private <T> ResponseEntity<T> res(HttpStatus status) {
        return Global.res(status);
    }

    private String uppercaseFirst(String input) {
        return input.substring(0, 1).toUpperCase() + input.substring(1);
    }

    // Auth endpoints
    @PostMapping(path = "/login", produces = "application/json")
    @ResponseBody 
    public ResponseEntity<String> verifyLogin(@RequestBody AuthRequest authRequest, HttpServletRequest request, HttpServletResponse response) {
        try (PreparedStatement ps = Global.getDB().prepareStatement("SELECT id, password FROM users WHERE username = ?")) {
            ps.setString(1, authRequest.username());
            ResultSet result = ps.executeQuery();
            if (result.next()) {
                String id = result.getString("id");
                if (encoder.matches(authRequest.password(), result.getString("password"))) {
                    Cookie tokenCookie = new Cookie("token", Jwts.builder().subject(id).issuedAt(new Date()).expiration(Date.from(new Date().toInstant().plus(Duration.ofHours(24)))).signWith(global.jwtKey).compact());
                    tokenCookie.setHttpOnly(true);
                    tokenCookie.setPath("/");
                    response.addCookie(tokenCookie);

                    Cookie UIDCookie = new Cookie("uid", id);
                    UIDCookie.setPath("/");
                    response.addCookie(UIDCookie);
                    
                    return ResponseEntity.ok().build();
                } else return res(HttpStatus.UNAUTHORIZED);
            } else return res(HttpStatus.NOT_FOUND);
        } catch (SQLException ex) {
            ex.printStackTrace();
            log.warn("Checking the login for user with username {} and password {} failed. Error: {}", new Object[]{authRequest.username(), authRequest.password(), ex.getMessage()});
            return res(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    public record AuthRequest(String username, String password) {}
    @PostMapping(path = "/signup", produces = "application/json")
    @ResponseBody 
    public ResponseEntity<Void> createLogin(@RequestBody SignupRequest signupRequest, HttpServletRequest request, HttpServletResponse response) {
        try (PreparedStatement ps = Global.getDB().prepareStatement("INSERT INTO users (id, pfp, username, password, displayname, bio, followers) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
            String uid = Global.newID();
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
                // Token cookie
                Cookie tokenCookie = new Cookie("token", Jwts.builder().subject(uid).issuedAt(new Date()).expiration(Date.from(new Date().toInstant().plus(Duration.ofHours(24)))).signWith(global.jwtKey).compact());
                tokenCookie.setHttpOnly(true);
                response.addCookie(tokenCookie);

                // User ID cookie
                Cookie UIDCookie = new Cookie("uid", uid);
                UIDCookie.setPath("/");
                response.addCookie(UIDCookie);

                return ResponseEntity.ok().build();
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
            return res(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    public record SignupRequest(String username, String password) {}

    // Set Bio & Display Name endpoints
    @PostMapping(path = "/bio-display/{userid}")
    public ResponseEntity<Void> setBioAndDisplay(@PathVariable String userid, @RequestBody BioAndDisplay bioAndDisplay, HttpServletRequest request) {
        if (global.checkAuth(request, userid)) {
            try (PreparedStatement ps = Global.getDB().prepareStatement("UPDATE users SET bio = ?, displayname = ? WHERE id = ?")) {
                ps.setString(1, bioAndDisplay.bio());
                ps.setString(2, bioAndDisplay.display());
                ps.setString(3, userid);
                int affected = ps.executeUpdate();
                if (affected == 0) return res(HttpStatus.INTERNAL_SERVER_ERROR);
                else return ResponseEntity.ok().build();
            } catch (SQLException ex) {
                return res(HttpStatus.INTERNAL_SERVER_ERROR);
            }
        } else return res(HttpStatus.UNAUTHORIZED);
    }
    public record BioAndDisplay(String bio, String display) {}

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
