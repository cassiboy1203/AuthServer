import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;

enum ActionCodes {
    None(0x00),
    Connect(0x01),
    Login(0x02),
    LoginInfo(0x03),
    NewUser(0x04),
    Logout(0x05),

    ;

    private int value;

    ActionCodes(int value) {
        this.value = value;
    }

    public void setValue(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}

enum ReplyCodes {
    InvalidAction(0x00),
    InvalidKey(0x01),
    ConnectionSuccessful(0x02),
    ConnectionFailed(0x03),
    LoginSuccessful(0x04),
    LoginFailed(0x05),
    UserCreate(0x06),
    EmailInUse(0x07),
    UserLoggedOut(0x08),

    ;
    private int value;

    ReplyCodes(int value) {
        this.value = value;
    }

    public void setValue(int value) {
        this.value = value;
    }

    public int getValue() {
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
            String data = null;
            InputStream input = socket.getInputStream();
            InputStreamReader reader = new InputStreamReader(input);

            BufferedReader bReader = new BufferedReader(reader);
            while (bReader.ready()) {
                data = bReader.readLine();
            }

            String[] splitString = data.toString().split(",");

            ActionCodes action = ActionCodes.None;
            action.setValue(Integer.parseInt(splitString[0]));

            if (action == ActionCodes.Connect){
                User user = new User(socket, users);
            }


        } catch (IOException e) {
            e.printStackTrace();
            System.out.println(e.getMessage());
        }
    }
}
