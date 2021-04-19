import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;

enum ActionCodes {
    None((byte) 0x00),
    Connect((byte) 0x01),
    Login((byte) 0x02),
    LoginInfo((byte) 0x03),
    NewUser((byte) 0x04),
    Logout((byte) 0x05),

    ;

    private byte value;

    ActionCodes(byte value) {
        this.value = value;
    }

    public void setValue(byte value) {
        this.value = value;
    }

    public byte getValue() {
        return value;
    }
}

enum ReplyCodes {
    InvalidAction((byte) 0x00),
    InvalidKey((byte) 0x01),
    ConnectionSuccessful((byte) 0x02),
    ConnectionFailed((byte) 0x03),
    LoginSuccessful((byte) 0x04),
    LoginFailed((byte) 0x05),
    UserCreate((byte) 0x06),
    EmailInUse((byte) 0x07),
    UserLoggedOut((byte) 0x08),

    ;
    private byte value;

    ReplyCodes(byte value) {
        this.value = value;
    }

    public void setValue(byte value) {
        this.value = value;
    }

    public byte getValue() {
        return value;
    }
}

public class AuthServer {
    public static ServerSocket serverSocket;
    public static int ServerPort = 61234;

    public static ArrayList<User> users = null;

    public static void OpenServer() {
        try {
            serverSocket = new ServerSocket(ServerPort);
        } catch (IOException e) {
            e.printStackTrace();
        }

        AcceptConnection();
    }

    public static void AcceptConnection() {
        try {
            Socket socket = serverSocket.accept();
            Thread serverThread = new Thread(AuthServer::AcceptConnection);
            serverThread.start();
            InputStream input = socket.getInputStream();
            byte[] buffer = new byte[1];
            input.read(buffer, 0, 1);

            ActionCodes action = ActionCodes.None;
            action.setValue(buffer[0]);

            if (action == ActionCodes.Connect){
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
