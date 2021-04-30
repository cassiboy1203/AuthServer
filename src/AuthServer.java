import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;

// the codes of the actions that can be executed.
enum ActionCodes {
    None((byte) 0x00),
    Connect((byte) 0x01),
    Login((byte) 0x02),
    LoginInfo((byte) 0x03),
    NewUser((byte) 0x04),
    Logout((byte) 0x05),
    Disconnect((byte) 0x06),
    AddFriend((byte) 0x07),
    GetFriends((byte) 0x08),
    GetFriendRequest((byte) 0x09),
    AcceptRequest((byte) 0x0A),
    RejectRequest((byte) 0x0B),
    BlockUser((byte) 0x0C),
    UnblockUser((byte) 0x0D),
    GetBlockedUsers((byte) 0x0E),

    ;

    private byte value;

    ActionCodes(byte value) {
        this.value = value;
    }

    public static ActionCodes fromValue(byte value){
        for (ActionCodes codes : values()){
            if (codes.getValue() == value){
                return codes;
            }
        }
        return null;
    }

    public byte getValue() {
        return value;
    }
}

// the codes that get send back.
enum ReplyCodes {
    InvalidAction((byte) 0x00),
    InvalidKey((byte) 0x01),
    InvalidArgs((byte) 0x02),
    ConnectionSuccessful((byte) 0x03),
    LoginSuccessful((byte) 0x04),
    LoginFailed((byte) 0x05),
    UserCreate((byte) 0x06),
    EmailInUse((byte) 0x07),
    UserLoggedOut((byte) 0x08),
    Confirm((byte) 0x09),
    FriendRequestSend((byte) 0x0A),
    UserNotFound((byte) 0x0B),
    FriendRequestExists((byte) 0x0C),
    FriendsFound((byte) 0x0D),
    FriendsNotFound((byte) 0x0E),

    ;
    private byte value;

    ReplyCodes(byte value) {
        this.value = value;
    }

    public static ReplyCodes fromValue(byte value){
        for (ReplyCodes codes : values()){
            if (codes.getValue() == value){
                return codes;
            }
        }
        return null;
    }

    public byte getValue() {
        return value;
    }
}

public class AuthServer {

    public static ServerSocket serverSocket;
    public static int ServerPort = 61234;

    public static ArrayList<User> users = new ArrayList<User>();

    // opens the server.
    public static void OpenServer() {
        try {
            serverSocket = new ServerSocket(ServerPort);
        } catch (IOException e) {
            e.printStackTrace();
        }

        AcceptConnection();
    }

    // accepts new connections.
    public static void AcceptConnection() {
        try {
            // waits for a new user to connect.
            Socket socket = serverSocket.accept();
            // starts new thread to allow multiple users to connect.
            Thread serverThread = new Thread(AuthServer::AcceptConnection);
            serverThread.start();
            // reads the action to be executed.
            InputStream input = socket.getInputStream();
            byte[] buffer = new byte[1];
            input.read(buffer, 0, 1);

            ActionCodes action = ActionCodes.fromValue(buffer[0]);

            // checks if the action code send = connect.
            if (action == ActionCodes.Connect){
                // connects the user.
                User user = new User(socket, users);
            } else {
                buffer = new byte[1];
                buffer[0] = ReplyCodes.InvalidAction.getValue();

                OutputStream out = socket.getOutputStream();
                out.write(buffer);

                socket.close();
            }

        } catch (IOException e) {
            e.printStackTrace();
            System.out.println(e.getMessage());
        }
    }
}
