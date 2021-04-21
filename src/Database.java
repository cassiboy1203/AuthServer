import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.sql.*;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;
import java.util.Random;

public class Database {
    // connects to the database.
    private static Connection ConnectToDatabase() {
            //Class.forName("com.mysql.cj.jdbc.Driver");

        try {
            return DriverManager.getConnection("jdbc:mysql://localhost/ChatServer?" + "user=root&password=");
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }
        return null;
    }

    // adds a new user to the database.
    public static boolean AddUserToDatabase(String name, String email, String pass, UserRole role, UserStatus status){
        Connection con;

        try {
            con = ConnectToDatabase();

            // prepares the statement.
            assert con != null;
            PreparedStatement pStatement = con.prepareStatement("INSERT INTO Users(UserName, UserEmail, UserPass, Salt, UserRole, UserStatus) VALUES(?,?,?,?,?,?)");
            pStatement.setString(1,name);
            pStatement.setString(2,email);

            // generates the salt.
            SecureRandom random = new SecureRandom();
            byte[] saltByte = new byte[16];
            random.nextBytes(saltByte);

            // hashes the salt and the password + hashed salt.
            String salt = GenerateSalt(100).get();
            String hashPass = HashPass(pass, salt).get();

            // prepares the rest of the statement.
            pStatement.setString(3,hashPass);
            pStatement.setString(4,salt);
            pStatement.setInt(5, role.getValue());
            pStatement.setInt(6,status.getValue());

            // checks if it was successful.
            boolean returnValue = pStatement.executeUpdate() != 0;

            // closes the connection.
            con.close();

            System.gc();

            return returnValue;

        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }

        return false;
    }

    // checks if the entered email and password is valid.
    public static boolean CheckLogin(String email, String pass, User user, String ip){
        Connection con = null;
        ResultSet result = null;

        try {
            con = ConnectToDatabase();

            // prepares the statement.
            assert con != null;
            PreparedStatement pStatement = con.prepareStatement("SELECT * FROM Users WHERE UserEmail = ?");
            pStatement.setString(1, email);

            // reads the values that has been send back.
            result = pStatement.executeQuery();
            if (result.next()){
                // check the password.
                String salt = result.getString("Salt");
                String savedPass = result.getString("UserPass");
                if (VerifyPassword(pass, savedPass, salt)){
                    // if the password is valid return extracts the user info.
                    user.Id = result.getInt("UserId");
                    user.Name = result.getString("UserName");
                    user.Email = email;
                    user.Image = user.Id + result.getString("UserImageType");
                    user.Role = UserRole.fromValue(result.getByte("UserRole"));
                    user.Status = UserStatus.fromValue(result.getByte("UserStatus"));

                    // closes the connection and reader.
                    result.close();
                    con.close();

                    // updates user status
                    UpdateUserStatus(user.Id, user.Status);
                    return true;
                }
                else {
                    result.close();
                    con.close();
                    return false;
                }
            }
        } catch (SQLException throwables) {
            throwables.printStackTrace();

            // closes the connection and reader.
            try {
                assert result != null;
                result.close();
                con.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return false;
        }
        return false;
    }

    public static boolean CheckLoginInfo(String loginInfo, User user, String ip){
        Connection con = null;
        ResultSet result = null;

        try {
            con = ConnectToDatabase();

            assert con != null;
            // prepares the statement.
            PreparedStatement pStatement = con.prepareStatement("SELECT * FROM Logins l, Users u WHERE LoginString = ? AND u.UserId = l.UserId AND IsLoggedIn = 1");
            pStatement.setString(1, loginInfo);
            result = pStatement.executeQuery();

            // reads the user info.
            if (result.first()){
                // check if the last login location was the same.
                if (result.getString("LastLoginLocation").equals(ip)){
                    user.Id = result.getInt("UserId");
                    user.Name = result.getString("UserName");
                    user.Email = result.getString("User");
                    user.Image = user.Id + result.getString("UserImageType");
                    user.Role = UserRole.fromValue(result.getByte("UserRole"));
                    user.Status = UserStatus.fromValue(result.getByte("UserStatus"));
                    UpdateUserStatus(user.Id, user.Status);
                    return true;
                }
            }
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }

        return false;
    }

    // updates the last know login info.
    public static boolean UpdateLoginInfo(int timestamp, int userId, String loginHash, String ip){
        Connection con = null;
        ResultSet result = null;

        try {
            con = ConnectToDatabase();

            assert con != null;
            // prepares the statement.
            PreparedStatement pStatement = con.prepareStatement("SELECT * FROM Logins WHERE UserId = ? AND IsLoggedIn = 1");
            pStatement.setInt(1,userId);

            result = pStatement.executeQuery();

            // sets old login info the be no longer valid.
            while (result.next()){
                pStatement = con.prepareStatement("UPDATE Logins Set IsLoggedIn = 0 WHERE LoginId = ?");
                pStatement.setInt(1, result.getInt("LoginId"));
                pStatement.executeUpdate();
            }

            result.close();

            // prepares the statement for the new login info.
            pStatement = con.prepareStatement("INSERT INTO Logins(LoginTimeStamp, IsLoggedIn, UserId, LoginToken, LastLoginLocation) VALUES(?,1,?,?,?)");
            pStatement.setInt(1,timestamp);
            pStatement.setInt(2,userId);
            pStatement.setString(3,loginHash);
            pStatement.setString(4,ip);

            // checks if it was successful.
            boolean returnValue = pStatement.executeUpdate() != 0;

            // closes the connection.
            con.close();

            return returnValue;

        } catch (SQLException throwables) {
            throwables.printStackTrace();
            // closes the connection and reader.
            try {
                assert result != null;
                result.close();
                con.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

        return false;
    }

    // changes the users current status in the database.
    public static boolean UpdateUserStatus(int id, UserStatus status){
        Connection con = null;

        try {
            con = ConnectToDatabase();

            assert con != null;
            // prepares the statement.
            PreparedStatement pStatement = con.prepareStatement("UPDATE Users SET UserStatus = ? WHERE UserId = ?");
            pStatement.setInt(1, status.getValue());
            pStatement.setInt(2, id);

            // checks if it was successful.
            boolean returnValue = pStatement.executeUpdate() != 0;

            // closes the connection.
            con.close();

            return returnValue;
        } catch (SQLException throwables) {
            throwables.printStackTrace();
            // closes the connection.
            try {
                con.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    private static final SecureRandom RAND = new SecureRandom();
    private static final int ITERATIONS = 65536;
    private static final int KEY_LENGTH = 512;
    private static final String ALGORITHM = "PBKDF2WithHmacSHA512";

    private static Optional<String> GenerateSalt(final int length){

        if (length < 1){
            System.err.println("error in generateSalt: length must be > 0");
            return Optional.empty();
        }

        byte[] salt = new byte[length];
        RAND.nextBytes(salt);

        return Optional.of(Base64.getEncoder().encodeToString(salt));
    }


    // hashes the string using SHA-512.
    public static Optional<String> HashPass(String pass, String salt){
        char[] chars = pass.toCharArray();
        byte[] bytes = salt.getBytes();

        PBEKeySpec spec = new PBEKeySpec(chars, bytes, ITERATIONS, KEY_LENGTH);
        Arrays.fill(chars, Character.MIN_VALUE);

        try {
            SecretKeyFactory fac = SecretKeyFactory.getInstance(ALGORITHM);
            byte[] securePassword = fac.generateSecret(spec).getEncoded();
            return Optional.of(Base64.getEncoder().encodeToString(securePassword));
        } catch (NoSuchAlgorithmException | InvalidKeySpecException ex) {
            System.err.println("Exception encounterd in hasPassword()");
            return Optional.empty();
        }
        finally {
            spec.clearPassword();
        }
    }

    public static Optional<String> GenerateLoginToken(){
        byte[] token = new byte[32];
        RAND.nextBytes(token);

        return Optional.of(Base64.getEncoder().encodeToString(token));
    }

    private static boolean VerifyPassword (String pass, String key, String salt){
        Optional<String> optEncrypted = HashPass(pass, salt);
        if (!optEncrypted.isPresent()) return false;
        return optEncrypted.get().equals(key);
    }
}
