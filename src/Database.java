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

    public static boolean AddUserToDatabase(String name, String email, String pass, UserRole role, String image, UserStatus status){
        Connection con = null;
        ResultSet result = null;

        try {
            con = ConnectToDatabase();

            assert con != null;
            PreparedStatement pStatement = con.prepareStatement("INSERT INTO Users(UserName, UserEmail, UserPass, Salt, UserRole, UserImageType, UserStatus) VALUES(?,?,?,?,?,?,?)");
            pStatement.setString(1,name);
            pStatement.setString(2,,email);

            SecureRandom random = new SecureRandom();
            byte[] saltByte = new byte[16];
            random.nextBytes(saltByte);

            String salt = HashPass(new String(saltByte, StandardCharsets.UTF_8));
            String hashPass = HashPass(pass + salt);

            pStatement.setString(3,hashPass);
            pStatement.setString(4,salt);
            pStatement.setInt(5, role.getValue());
            pStatement.setString(6,image);
            pStatement.setInt(7,status.getValue());

            if (pStatement.executeUpdate() != 0) return true;

        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }

        return false;
    }

    public static boolean CheckLogin(String email, String pass, User user){
        Connection con = null;
        ResultSet result = null;

        try {
            con = ConnectToDatabase();

            assert con != null;
            PreparedStatement pStatement = con.prepareStatement("SELECT * FROM Users WHERE UserEmail = ?");
            pStatement.setString(1, email);

            result = pStatement.executeQuery();

            if (result.first()){
                if (result.getString("UserPass").equals(HashPass(pass + result.getString("Salt")))){
                    user.Id = result.getInt("UserId");
                    user.Name = result.getString("UserName");
                    user.Email = email;
                    user.Image = user.Id + result.getString("UserImageType");
                    user.Role.setValue(result.getInt("UserRole"));
                    user.status.setValue(result.getInt("UserStatus"));

                    result.close();
                    con.close();
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
