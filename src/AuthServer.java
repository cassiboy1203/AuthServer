import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

enum ActionCodes {
    InvalidAction(0xFF),
    None(0x00),
    Login(0x01),
    LoginInfo(0x02),
    NewUser(0x03);

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

public class AuthServer {
    public static ServerSocket serverSocket;
    public static int ServerPort = 61234;

    public static void OpenServer(){
        try {
            serverSocket = new ServerSocket(ServerPort);
        } catch (IOException e) {
            e.printStackTrace();
        }

        AcceptConnection();
    }

    public static void AcceptConnection(){
        try {
            Thread serverThread = new Thread(AuthServer::AcceptConnection);
            serverThread.start();

            Socket socket = serverSocket.accept();
            InputStream input = socket.getInputStream();
            InputStreamReader reader = new InputStreamReader(input);

            int character;
            StringBuilder data = new StringBuilder();

            while ((character = reader.read()) != -1){
                data.append((char) character);
            }

            String[] splitString = data.toString().split(",");

            ActionCodes action = ActionCodes.None;
            action.setValue(Integer.parseInt(splitString[0]));

            User user = null;
            switch (action){
                case None:
                    OutputStream out = socket.getOutputStream();

                    byte[] outData;
                    String message = String.format("{}, Invalid Action", Integer.toHexString(ActionCodes.InvalidAction.getValue()));

                    outData = message.getBytes(StandardCharsets.UTF_8);
                    out.write(outData);

                    PrintWriter writer = new PrintWriter(out, true);
                    break;
                case Login:
                    user = new User(splitString[1], splitString[2], socket);
                    break;
                case LoginInfo:
                    user = new User(splitString[1], socket);
                    break;
                case NewUser:
                    user = new User(splitString[1], splitString[2], splitString[3], splitString[4], socket);
                    break;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
