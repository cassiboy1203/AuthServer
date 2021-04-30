import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.sql.*;
import java.util.*;

public class Database {
    // connects to the database.
    private static Connection ConnectToDatabase() {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            return DriverManager.getConnection("jdbc:mysql://localhost/ChatServer?" + "user=root&password=");
        } catch (SQLException | ClassNotFoundException throwables) {
            throwables.printStackTrace();
        }
        return null;
    }

    // adds a new user to the database.
    public static boolean AddUserToDatabase(String name, String email, String pass, UserRole role, UserStatus status) {
        Connection con = ConnectToDatabase();
        ResultSet result = null;
        PreparedStatement pStatement = null;

        try {
            String friendCode = GenerateFriendCode(name).get();
            // prepares the statement.
            assert con != null;
            pStatement = con.prepareStatement("INSERT INTO Users(UserName, UserEmail, UserPass, Salt, UserRole, UserStatus, FriendCode) VALUES(?,?,?,?,?,?,?)");
            pStatement.setString(1, name);
            pStatement.setString(2, email);

            // generates the salt.
            SecureRandom random = new SecureRandom();
            byte[] saltByte = new byte[16];
            random.nextBytes(saltByte);

            // hashes the salt and the password + hashed salt.
            String salt = GenerateSalt(100).get();
            String hashPass = HashPass(pass, salt).get();

            // prepares the rest of the statement.
            pStatement.setString(3, hashPass);
            pStatement.setString(4, salt);
            pStatement.setInt(5, role.getValue());
            pStatement.setInt(6, status.getValue());
            pStatement.setString(7, friendCode);

            // checks if it was successful.
            pStatement.executeUpdate();

            pStatement = con.prepareStatement("SELECT LAST_INSERT_ID() AS UserId");

            result = pStatement.executeQuery();

            String userToken = GenerateUserToken(5).get();

            result.next();
            pStatement = con.prepareStatement("INSERT INTO UserTokens(UserId, UserToken) VALUES(?,?)");
            pStatement.setInt(1, result.getInt("UserId"));
            pStatement.setString(2, userToken);

            System.out.println(result.getInt("UserId"));

            pStatement.executeUpdate();

            return true;

        } catch (SQLException throwables) {
            throwables.printStackTrace();
            return false;
        } finally {
            try {
                //closes the connection and reader.
                if (result != null) result.close();
                if (pStatement != null) pStatement.close();
                if (con != null) con.close();
            } catch (SQLException throwables) {
                throwables.printStackTrace();
            }
            System.gc();
        }
    }

    // checks if the entered email and password is valid.
    public static boolean CheckLogin(String email, String pass, User user) {
        Connection con = null;
        ResultSet result = null;
        PreparedStatement pStatement = null;

        try {
            con = ConnectToDatabase();

            // prepares the statement.
            assert con != null;
            pStatement = con.prepareStatement("SELECT * FROM Users u, UserTokens ut WHERE UserEmail = ? AND u.UserId = ut.UserId");
            pStatement.setString(1, email);

            // reads the values that has been send back.
            result = pStatement.executeQuery();
            if (result.next()) {
                // check the password.
                String salt = result.getString("Salt");
                String savedPass = result.getString("UserPass");
                if (VerifyPassword(pass, savedPass, salt)) {
                    // if the password is valid return extracts the user info.
                    user.Id = result.getInt("UserId");
                    user.Name = result.getString("UserName");
                    user.Email = email;
                    user.Image = user.Id + result.getString("UserImageType");
                    user.Role = UserRole.fromValue(result.getByte("UserRole"));
                    user.Status = UserStatus.fromValue(result.getByte("UserStatus"));
                    user.FriendCode = result.getString("FriendCode");
                    user.UserToken = result.getString("UserToken");

                    // closes the connection and reader.
                    result.close();
                    con.close();

                    // updates user status
                    UpdateUserStatus(user.Id, user.Status);
                    return true;
                } else {
                    return false;
                }
            }
        } catch (SQLException throwables) {
            throwables.printStackTrace();
            return false;
        } finally {
            try {
                //closes the connection and reader.
                if (result != null) result.close();
                if (pStatement != null) pStatement.close();
                if (con != null) con.close();
            } catch (SQLException throwables) {
                throwables.printStackTrace();
            }
            System.gc();
        }

        return false;
    }

    public static boolean CheckLoginInfo(String loginToken, String userToken, User user, String ip) {
        Connection con = null;
        ResultSet result = null;
        PreparedStatement pStatement = null;

        try {
            con = ConnectToDatabase();

            assert con != null;
            // prepares the statement.
            pStatement = con.prepareStatement("SELECT * FROM Logins l, Users u, UserTokens ut WHERE ut.UserToken = ? AND u.UserId = l.UserId AND IsLoggedIn = 1 AND ut.UserId = u.UserId");
            pStatement.setString(1, userToken);
            result = pStatement.executeQuery();

            // reads the user info.
            if (result.next()) {
                // check if the last login location was the same.
                if (result.getString("LastLoginLocation").equals(ip)) {
                    String loginHash = HashPass(loginToken, Integer.toString(result.getInt("LoginTimeStamp"))).get();
                    String savedLoginToken = result.getString("LoginToken");
                    if (Objects.equals(loginHash, savedLoginToken)) {

                        user.Id = result.getInt("UserId");
                        user.Name = result.getString("UserName");
                        user.Email = result.getString("UserEmail");
                        user.Image = user.Id + result.getString("UserImageType");
                        user.Role = UserRole.fromValue(result.getByte("UserRole"));
                        user.FriendCode = result.getString("FriendCode");
                        UpdateUserStatus(user.Id, user.Status);
                        return true;
                    }
                }
            }
        } catch (
                SQLException throwables) {
            throwables.printStackTrace();
        } finally {
            try {
                //closes the connection and reader.
                if (result != null) result.close();
                if (pStatement != null) pStatement.close();
                if (con != null) con.close();
            } catch (SQLException throwables) {
                throwables.printStackTrace();
            }
            System.gc();
        }

        return false;
    }

    // updates the last know login info.
    public static boolean UpdateLoginInfo(int timestamp, int userId, String loginHash, String ip) {
        Connection con = null;
        ResultSet result = null;
        PreparedStatement pStatement = null;

        try {
            con = ConnectToDatabase();

            assert con != null;
            // prepares the statement.
            pStatement = con.prepareStatement("SELECT * FROM Logins WHERE UserId = ? AND IsLoggedIn = 1");
            pStatement.setInt(1, userId);

            result = pStatement.executeQuery();

            // sets old login info the be no longer valid.
            while (result.next()) {
                pStatement = con.prepareStatement("UPDATE Logins Set IsLoggedIn = 0 WHERE LoginId = ?");
                pStatement.setInt(1, result.getInt("LoginId"));
                pStatement.executeUpdate();
            }

            result.close();

            // prepares the statement for the new login info.
            pStatement = con.prepareStatement("INSERT INTO Logins(LoginTimeStamp, IsLoggedIn, UserId, LoginToken, LastLoginLocation) VALUES(?,1,?,?,?)");
            pStatement.setInt(1, timestamp);
            pStatement.setInt(2, userId);
            pStatement.setString(3, loginHash);
            pStatement.setString(4, ip);

            // checks if it was successful.
            boolean returnValue = pStatement.executeUpdate() != 0;

            // closes the connection.
            con.close();

            return returnValue;

        } catch (SQLException throwables) {
            throwables.printStackTrace();
        } finally {
            try {
                //closes the connection and reader.
                if (result != null) result.close();
                if (pStatement != null) pStatement.close();
                if (con != null) con.close();
            } catch (SQLException throwables) {
                throwables.printStackTrace();
            }
            System.gc();
        }

        return false;
    }

    // changes the users current status in the database.
    public static boolean UpdateUserStatus(int id, UserStatus status) {
        Connection con = null;
        PreparedStatement pStatement = null;

        try {
            con = ConnectToDatabase();

            assert con != null;
            // prepares the statement.
            pStatement = con.prepareStatement("UPDATE Users SET UserStatus = ?, LastUpdate = ? WHERE UserId = ?");
            pStatement.setInt(1, status.getValue());
            pStatement.setInt(2, GetTimeStamp());
            pStatement.setInt(3, id);

            // checks if it was successful.
            boolean returnValue = pStatement.executeUpdate() != 0;

            // closes the connection.
            con.close();

            return returnValue;
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        } finally {
            try {
                //closes the connection and reader.
                if (pStatement != null) pStatement.close();
                if (con != null) con.close();
            } catch (SQLException throwables) {
                throwables.printStackTrace();
            }
            System.gc();
        }

        return false;
    }

    // saves FriendRequest in database
    public static int SendFriendRequest(int id, String userName, String friendCode){
        Connection con = ConnectToDatabase();
        PreparedStatement pStatement = null;
        ResultSet result = null;

        try {
            // gets the friend id.
            pStatement = con.prepareStatement("SELECT * FROM Users WHERE UserName = ? AND FriendCode = ?");
            pStatement.setString(1, userName);
            pStatement.setString(2, friendCode);

            // executes the statement
            result = pStatement.executeQuery();

            // reads the results
            if (result.next()){
                int friendId = result.getInt("UserId");

                pStatement = con.prepareStatement("SELECT * FROM FriendRequests WHERE UserSendRequest = ? AND UserReceivedRequest = ?");
                pStatement.setInt(1, id);
                pStatement.setInt(2, friendId);

                result = pStatement.executeQuery();

                if (result.next()){
                    return 0;
                }

                // add the request to the database.
                pStatement = con.prepareStatement("INSERT INTO FriendRequests(UserSendRequest, UserReceivedRequest, LastUpdateTime) VALUES(?,?,?)");
                pStatement.setInt(1, id);
                pStatement.setInt(2, friendId);
                pStatement.setInt(3,GetTimeStamp());

                pStatement.executeUpdate();

                return 1;
            }
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }
        finally {
            try {
                if (result != null) result.close();
                if (pStatement != null) pStatement.close();
                if (con != null) con.close();
            } catch (SQLException throwables) {
                throwables.printStackTrace();
            }
        }
        return -1;
    }

    public static ArrayList<Friend> GetFriends(int id, int lastUpdateTime){
        Connection con = ConnectToDatabase();
        PreparedStatement pStatement = null;
        ResultSet result = null;
        ArrayList<Friend> friends = new ArrayList<>();

        try {
            pStatement = con.prepareStatement("SELECT u.*, ut.UserToken FROM Users u, FriendList f, UserTokens ut WHERE f.FriendId = u.UserId AND f.FriendId = ut.UserId AND f.UserId = ? AND (f.LastUpdate >= ? OR u.LastUpdate >= ?)");
            pStatement.setInt(1, id);
            pStatement.setInt(2, lastUpdateTime);
            pStatement.setInt(3, lastUpdateTime);

            result = pStatement.executeQuery();

            while (result.next()){
                Friend friend = new Friend();
                friend.token = result.getString("UserToken");
                friend.Name = result.getString("UserName");
                friend.status = UserStatus.fromValue(result.getByte("UserStatus"));
                //TODO: Get image.

                friends.add(friend);
            }

            ArrayList<Friend> friendList = new ArrayList<>();
            for (Friend friend : friends){
                pStatement = con.prepareStatement("SELECT (bul.UserId = ? AND bul.BlockedUser = ut.UserId AND bul.IsBlocked = 1) OR (bul.UserId = ut.UserId AND bul.BlockedUser = ? AND bul.IsBlocked = 1) as IsBlocked FROM BlockedUsersList bul, UserTokens ut WHERE ut.UserToken = ?");
                pStatement.setInt(1, id);
                pStatement.setInt(2, id);
                pStatement.setString(3, friend.token);

                result = pStatement.executeQuery();

                boolean isBlocked = false;
                while (result.next()){
                    if (result.getBoolean("IsBlocked")){
                        isBlocked = true;
                        break;
                    }
                }
                if (!isBlocked){
                    friendList.add(friend);
                }
            }

            if (friendList.isEmpty()) return null;
            return friendList;
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        } finally {
            try {
                if (result != null) result.close();
                if (pStatement != null) pStatement.close();
                if (con != null) con.close();
            } catch (SQLException throwables) {
                throwables.printStackTrace();
            }
        }
        return null;
    }

    public static ArrayList<Friend> GetFriendRequests(int id, int lastUpdateTime){
        Connection con = ConnectToDatabase();
        PreparedStatement pStatement = null;
        ResultSet result = null;
        ArrayList<Friend> friends = new ArrayList<>();

        try {
            pStatement = con.prepareStatement("SELECT * FROM FriendRequests f, Users u, UserTokens ut WHERE u.UserId = f.UserSendRequest AND ut.UserId = f.UserSendRequest AND f.UserReceivedRequest = ? AND f.RequestStatus = 0 AND (f.LastUpdate >= ? OR u.LastUpdate >= ?)");
            pStatement.setInt(1, id);
            pStatement.setInt(2, lastUpdateTime);
            pStatement.setInt(3, lastUpdateTime);

            result = pStatement.executeQuery();

            while (result.next()){
                Friend friend = new Friend();
                friend.Name = result.getString("UserName");
                friend.token = result.getString("UserToken");
                //TODO: get image

                friends.add(friend);
            }

            return friends;

        } catch (SQLException throwables) {
            throwables.printStackTrace();
        } finally {
            try {
                if (result != null) result.close();
                if (pStatement != null) pStatement.close();
                if (con != null) con.close();
            } catch (SQLException throwables) {
                throwables.printStackTrace();
            }
        }
        return null;
    }

    public static boolean UpdateRequestStatus(ActionCodes action, String userToken, int id){
        Connection con = ConnectToDatabase();
        PreparedStatement pStatement = null;
        ResultSet result = null;

        try {
            pStatement = con.prepareStatement("UPDATE FriendRequests f, UserTokens ut SET f.RequestStatus = ?, f.LastUpdate = ? WHERE ut.UserId = f.UserSendRequest AND f.UserReceivedRequest = ? AND ut.UserToken = ?");
            pStatement.setInt(1, action == ActionCodes.AcceptRequest ? 1 : -1);
            pStatement.setInt(2, GetTimeStamp());
            pStatement.setInt(3, id);
            pStatement.setString(4, userToken);

            pStatement.executeUpdate();

            if (action == ActionCodes.AcceptRequest){
                pStatement = con.prepareStatement("SELECT UserId From UserTokens WHERE UserToken = ?");
                pStatement.setString(1, userToken);

                result = pStatement.executeQuery();

                if (result.next()) {
                    int friendId = result.getInt("UserId");

                    pStatement = con.prepareStatement("INSERT INTO FriendList(UserId, FriendId, LastUpdate) VALUES(?,?,?)");
                    pStatement.setInt(1,id);
                    pStatement.setInt(2,friendId);

                    pStatement.executeUpdate();

                    pStatement = con.prepareStatement("INSERT INTO FriendList(UserId, FriendId, LastUpdate) VALUES(?,?,?)");
                    pStatement.setInt(2,id);
                    pStatement.setInt(1,friendId);

                    pStatement.executeUpdate();
                }
            }

            return true;
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        } finally {
            try {
                if (result != null) result.close();
                if (pStatement != null) pStatement.close();
                if (con != null) con.close();
            } catch (SQLException throwables) {
                throwables.printStackTrace();
            }
        }
        return false;
    }

    public static ArrayList<Friend> GetBlockedUsers(int id, int lastUpdateTime){
        Connection con = ConnectToDatabase();
        PreparedStatement pStatement = null;
        ResultSet result = null;

        try {
            pStatement = con.prepareStatement("SELECT bul.*, ut.*, u.UserName FROM Users u, BlockedUsersList bul, UserTokens ut WHERE u.UserId = bul.BlockedUser AND ut.UserId = bul.BlockedUser AND bul.IsBlocked = 1 AND bul.UserId = ? AND bul.LastUpdate >= ?");
            pStatement.setInt(1,id);
            pStatement.setInt(2, lastUpdateTime);

            result = pStatement.executeQuery();

            ArrayList<Friend> blockedUsers = new ArrayList<>();
            while (result.next()){
                Friend blockedUser = new Friend();
                blockedUser.token = result.getString("UserToken");
                blockedUser.Name = result.getString("UserName");
                //TODO: get user image

                blockedUsers.add(blockedUser);
            }

            return blockedUsers;
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        } finally {
            try {
                if (result != null) result.close();
                if (pStatement != null) pStatement.close();
                if (con != null) con.close();
            } catch (SQLException throwables) {
                throwables.printStackTrace();
            }
        }
        return null;
    }

    public static boolean BlockUser(int id, String UserToken){
        Connection con = ConnectToDatabase();
        PreparedStatement pStatement = null;
        ResultSet result = null;

        try {
            pStatement = con.prepareStatement("SELECT UserId FROM UserTokens WHERE UserToken = ?");
            pStatement.setString(1, UserToken);

            result = pStatement.executeQuery();

            if (result.next()){
                int blockId  = result.getInt("UserId");

                pStatement = con.prepareStatement("SELECT IsBlocked FROM BlockedUsersList WHERE UserId = ? AND BlockedUser = ? AND IsBlocked = 1");
                pStatement.setInt(1, id);
                pStatement.setInt(2, blockId);

                result = pStatement.executeQuery();

                if (result.next()) return false;

                pStatement = con.prepareStatement("INSERT INTO blockeduserslist(UserId, BlockedUser, LastUpdate) VALUES (?,?,?)");
                pStatement.setInt(1, id);
                pStatement.setInt(2, blockId);
                pStatement.setInt(3, GetTimeStamp());

                pStatement.executeUpdate();

                return true;
            }
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        } finally {
            try {
                if (result != null) result.close();
                if (pStatement != null) pStatement.close();
                if (con != null) con.close();
            } catch (SQLException throwables) {
                throwables.printStackTrace();
            }
        }
        return false;
    }

    public static boolean UnBlockUser(int id, String UserToken){
        Connection con = ConnectToDatabase();
        PreparedStatement pStatement = null;
        ResultSet result = null;

        try {
            pStatement = con.prepareStatement("SELECT UserId FROM UserTokens WHERE UserToken = ?");
            pStatement.setString(1, UserToken);

            result = pStatement.executeQuery();

            if (result.next()){
                int blockId  = result.getInt("UserId");

                pStatement = con.prepareStatement("UPDATE BlockedUsersList SET IsBlocked = 0, LastUpdate = ? WHERE Userid = ? AND BlockedUser = ?");
                pStatement.setInt(1, GetTimeStamp());
                pStatement.setInt(2, id);
                pStatement.setInt(3, blockId);

                pStatement.executeUpdate();

                return true;
            }
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        } finally {
            try {
                if (result != null) result.close();
                if (pStatement != null) pStatement.close();
                if (con != null) con.close();
            } catch (SQLException throwables) {
                throwables.printStackTrace();
            }
        }
        return false;
    }

    private static int GetTimeStamp(){
        return Long.valueOf(System.currentTimeMillis() / 1000L).intValue();
    }

    private static final SecureRandom RAND = new SecureRandom();
    private static final int ITERATIONS = 65536;
    private static final int KEY_LENGTH = 512;
    private static final String ALGORITHM = "PBKDF2WithHmacSHA512";

    private static Optional<String> GenerateSalt(final int length) {

        if (length < 1) {
            System.err.println("error in generateSalt: length must be > 0");
            return Optional.empty();
        }

        byte[] salt = new byte[length];
        RAND.nextBytes(salt);

        return Optional.of(Base64.getEncoder().encodeToString(salt));
    }


    // hashes the string using SHA-512.
    public static Optional<String> HashPass(String pass, String salt) {
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
        } finally {
            spec.clearPassword();
        }
    }

    public static Optional<String> GenerateLoginToken() {
        byte[] token = new byte[32];
        RAND.nextBytes(token);

        return Optional.of(Base64.getEncoder().encodeToString(token));
    }

    private static Optional<String> GenerateUserToken(int size) {
        while (true) {
            byte[] token = new byte[size];
            RAND.nextBytes(token);

            Optional<String> tokenString = Optional.of(Base64.getEncoder().encodeToString(token));

            if (CheckTokenUnique(tokenString.get())) {
                return tokenString;
            }
        }
    }

    private static boolean CheckTokenUnique(String token) {
        Connection con = ConnectToDatabase();
        ResultSet result = null;
        PreparedStatement pStatement = null;

        try {
            assert con != null;
            pStatement = con.prepareStatement("SELECT * FROM UserTokens WHERE UserToken = ?");
            pStatement.setString(1, token);

            result = pStatement.executeQuery();

            if (result != null) return !result.next();
            return false;

        } catch (SQLException throwables) {
            throwables.printStackTrace();
            return false;
        } finally {
            try {
                //closes the connection and reader.
                if (result != null) result.close();
                if (pStatement != null) pStatement.close();
                if (con != null) con.close();
            } catch (SQLException throwables) {
                throwables.printStackTrace();
            }
            System.gc();
        }
    }

    private static Optional<String> GenerateFriendCode(String name) {
        while (true) {
            int token = 0;
            token = RAND.nextInt(9999) + 1;

            Optional<String> tokenString = Optional.of(Integer.toString(token));

            if (CheckUniqueFriendCode(tokenString.get(), name)) {
                return tokenString;
            }
        }
    }

    private static boolean CheckUniqueFriendCode(String friendCode, String userName) {
        Connection con = ConnectToDatabase();
        ResultSet result = null;
        PreparedStatement pStatement = null;

        try {
            assert con != null;
            pStatement = con.prepareStatement("SELECT * FROM Users WHERE UserName = ? AND FriendCode = ?");
            pStatement.setString(1, userName);
            pStatement.setString(2, friendCode);

            result = pStatement.executeQuery();

            if (result != null) return !result.next();
            return false;

        } catch (SQLException throwables) {
            throwables.printStackTrace();
            return false;
        } finally {
            try {
                //closes the connection and reader.
                if (result != null) result.close();
                if (pStatement != null) pStatement.close();
                if (con != null) con.close();
            } catch (SQLException throwables) {
                throwables.printStackTrace();
            }
            System.gc();
        }
    }

    private static boolean VerifyPassword(String pass, String key, String salt) {
        Optional<String> optEncrypted = HashPass(pass, salt);
        if (!optEncrypted.isPresent()) return false;
        return optEncrypted.get().equals(key);
    }
}
