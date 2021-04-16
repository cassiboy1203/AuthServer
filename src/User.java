import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Random;

enum UserStatus{
    Online (0),
    Offline (1),
    Idle (2),
    Dnd (3),
    Invisible (4);

    private int value;

    UserStatus(int value){
        this.value = value;
    }

    public void setValue(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}

enum UserRole {
    User(0),
    Mod(29),
    Dev(30);

    public int value;

    UserRole(int value){
        this.value = value;
    }

    public void setValue(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}


public class User {

    public int Id;
    public String Name;
    public String Email;
    public UserRole Role = UserRole.User;
    public String Image;
    public UserStatus status;
    public boolean IsLoggedIn;

    public Socket Client;
    public String AuthKey;

    private ArrayList<User> users;

    public User(Socket client, ArrayList<User> users){
        this.Client = client;

        String message = String.format("{0},", ReplyCodes.ConnectionSuccessful.getValue());

        SendReply(message);

        AuthServer.users.add(this);
        this.users = users;

        ReceiveMessages();
    }

    private void ReceiveMessages(){
        while (true) {
            try {
                InputStream input = Client.getInputStream();
                InputStreamReader reader = new InputStreamReader(input);

                BufferedReader bReader = new BufferedReader(reader);

                while (bReader.ready()){
                    String[] buffer = bReader.readLine().split(",");

                    if (AuthKey == buffer[0]) {
                        ActionCodes action = ActionCodes.None;
                        action.setValue(Integer.parseInt(buffer[1]));
                        if (!IsLoggedIn) {
                            switch (action) {
                                case Login:
                                    CheckLogin(buffer[2], buffer[3]);
                                    break;
                                case LoginInfo:
                                    CheckLoginInfo(buffer[2]);
                                    break;
                                case NewUser:
                                    CreateUser(buffer[2], buffer[3], buffer[4]);
                                    break;
                                case None:
                                default:
                                    SendReply(Integer.toHexString(ReplyCodes.InvalidAction.getValue()));
                            }
                        } else {
                            switch (action){
                                case None:
                                default:
                                    SendReply(Integer.toHexString(ReplyCodes.InvalidAction.getValue()));
                            }
                        }
                    }
                    else {
                        SendReply(Integer.toHexString(ReplyCodes.InvalidKey.getValue()));
                    }
                }

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void CheckLogin(String email, String pass){
        if (Database.CheckLogin(email, pass, this, Client.getRemoteSocketAddress().toString())){
            IsLoggedIn = true;
            SendReply(Integer.toHexString(ReplyCodes.LoginSuccessful.getValue()));
        } else {
            SendReply(Integer.toHexString(ReplyCodes.LoginFailed.getValue()));
        }
    }

    private void CheckLoginInfo(String loginInfo){

    }

    private void CreateUser(String email, String pass, String name){
        if (Database.AddUserToDatabase(name,email,pass, UserRole.User, UserStatus.Offline)){
            SendReply(Integer.toHexString(ReplyCodes.UserCreate.getValue()));
        }else {
            SendReply(Integer.toHexString(ReplyCodes.EmailInUse.getValue()));
        }
    }

    private void SendReply(String message){
        try {
            message = message + (this.AuthKey = GenerateAuthKey(users));
            byte[] buffer;
            buffer = message.getBytes(StandardCharsets.UTF_8);

            OutputStream out = Client.getOutputStream();
            out.write(buffer);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private String GenerateAuthKey(ArrayList<User> users){
        while (true) {
            byte[] buffer = new byte[16];
            new Random().nextBytes(buffer);
            String key = new String(buffer, StandardCharsets.UTF_8);

            boolean isUnique = true;
            for (User user : users) {
                if (user.AuthKey == key) {
                    isUnique = false;
                    break;
                }
            }

            if (!isUnique){
                continue;
            }
            return key;
        }
    }
}
