import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.*;

public class Database {
    private static Connection ConnectToDatabase() {
        try {
            Class.forName("com.mysql.jdbc.Driver");

            return DriverManager.getConnection("jdbc:mysql://localhost/ChatServer?" + "user=root&password=");
        }
        catch (ClassNotFoundException | SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static boolean AddUserToDatabase(String name, String email, String pass, UserRole role, UserStatus status){
        Connection con;

        try {
            con = ConnectToDatabase();

            assert con != null;
            PreparedStatement pStatement = con.prepareStatement("INSERT INTO Users(UserName, UserEmail, UserPass, Salt, UserRole, UserStatus) VALUES(?,?,?,?,?,?)");
            pStatement.setString(1,name);
            pStatement.setString(2,email);

            SecureRandom random = new SecureRandom();
            byte[] saltByte = new byte[16];
            random.nextBytes(saltByte);

            String salt = HashPass(new String(saltByte, StandardCharsets.UTF_8));
            String hashPass = HashPass(pass + salt);

            pStatement.setString(3,hashPass);
            pStatement.setString(4,salt);
            pStatement.setInt(5, role.getValue());
            pStatement.setInt(6,status.getValue());

            if (pStatement.executeUpdate() != 0) return true;

        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }

        return false;
    }

    public static boolean CheckLogin(String email, String pass, User user, String ip){
        Connection con = null;
        ResultSet result = null;

        try {
            con = ConnectToDatabase();

            assert con != null;
            PreparedStatement pStatement = con.prepareStatement("SELECT * FROM Users WHERE UserEmail = ?");
            pStatement.setString(1, email);

            result = pStatement.executeQuery();

            if (result.first()){
                String hashPass = HashPass(pass + result.getString("Salt"));
                if (result.getString("UserPass").equals(hashPass)){
                    user.Id = result.getInt("UserId");
                    user.Name = result.getString("UserName");
                    user.Email = email;
                    user.Image = user.Id + result.getString("UserImageType");
                    user.Role.setValue(result.getInt("UserRole"));
                    user.status.setValue(result.getInt("UserStatus"));

                    result.close();
                    con.close();

                    int timeStamp = Long.valueOf(System.currentTimeMillis() / 1000L).intValue();
                    UpdateLoginInfo(timeStamp, user.Id, hashPass, ip);
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
            PreparedStatement pStatement = con.prepareStatement("SELECT * FROM Logins l, Users u WHERE LoginString = ? AND u.UserId = l.UserId");
            pStatement.setString(1, loginInfo);
            result = pStatement.executeQuery();

            if (result.first()){
                if (result.getString("LastLoginLocation").equals(ip)){
                    user.Id = result.getInt("UserId");
                    user.Name = result.getString("UserName");
                    user.Email = result.getString("User");
                    user.Image = user.Id + result.getString("UserImageType");
                    user.Role.setValue(result.getInt("UserRole"));
                    user.status.setValue(result.getInt("UserStatus"));

                    return true;
                }
            }
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }

        return false;
    }

    public static boolean UpdateLoginInfo(int timestamp, int userId, String pass, String ip){
        Connection con = null;
        ResultSet result = null;

        try {
            con = ConnectToDatabase();

            assert con != null;
            PreparedStatement pStatement = con.prepareStatement("SELECT * FROM Logins WHERE UserId = ? AND IsLoggedIn = 1");
            pStatement.setInt(1,userId);

            result = pStatement.executeQuery();

            while (result.next()){
                pStatement = con.prepareStatement("UPDATE Logins Set IsLoggedIn = 0 WHERE LoginId = ?");
                pStatement.setInt(1, result.getInt("LoginId"));
                pStatement.executeUpdate();
            }

            result.close();

            pStatement = con.prepareStatement("INSERT INTO Logins(LoginTimeStamp, IsLoggedIn, UserId, LoginString, LastLoginLocation) VALUES(?,1,?,?,?)");
            pStatement.setInt(1,timestamp);
            pStatement.setInt(2,userId);
            pStatement.setString(3,HashPass(Integer.toString(timestamp) + Integer.toString(userId) + pass));
            pStatement.setString(4,ip);
            if (pStatement.executeUpdate() != 0) return true;

        } catch (SQLException throwables) {
            throwables.printStackTrace();
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

    private static String HashPass(String stringToHash){
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-512");
            byte[] byteString = md.digest(stringToHash.getBytes(StandardCharsets.UTF_8));
            return new String(byteString, StandardCharsets.UTF_8);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            return null;
        }
    }
}
