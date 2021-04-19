import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
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
    public byte[] AuthKey;

    private ArrayList<User> users;

    public User(Socket client, ArrayList<User> users){
        this.Client = client;
        SendReply(ReplyCodes.ConnectionSuccessful.getValue());

        AuthServer.users.add(this);
        this.users = users;

        ReceiveMessages();
    }

    private void ReceiveMessages(){
        while (true) {
            try {
                InputStream input = Client.getInputStream();
                byte[] authKey = new byte[16];
                byte[] actionCode = new byte[1];
                byte[] messageLengthByte = new byte[4];
                int messageLength;
                input.read(authKey, 0, 16);
                input.read(actionCode, 0, 1);
                input.read(messageLengthByte, 0 ,4);
                messageLength = FromByteArray(messageLengthByte);
                byte[] buffer;
                buffer = ReadMessage(messageLength, input);
                if (buffer == null){
                    SendReply(ReplyCodes.InvalidArgs.getValue());
                } else {

                    if (Arrays.equals(AuthKey, authKey)) {
                        ActionCodes action = ActionCodes.None;
                        action.setValue(actionCode[0]);
                        String[] message = new String(buffer, StandardCharsets.UTF_8).split(",");
                        if (!IsLoggedIn) {
                            switch (action) {
                                case Login:
                                    CheckLogin(message[0], message[1]);
                                    break;
                                case LoginInfo:
                                    CheckLoginInfo(message[0]);
                                    break;
                                case NewUser:
                                    CreateUser(message[0], message[1], message[2]);
                                    break;
                                case None:
                                default:
                                    SendReply(ReplyCodes.InvalidAction.getValue());
                            }
                        } else {
                            switch (action) {
                                case None:
                                default:
                                    SendReply(ReplyCodes.InvalidAction.getValue());
                            }
                        }
                    } else {
                        SendReply(ReplyCodes.InvalidKey.getValue());
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
            SendReply(ReplyCodes.LoginSuccessful.getValue());
        } else {
            SendReply(ReplyCodes.LoginFailed.getValue());
        }
    }

    private void CheckLoginInfo(String loginInfo){

    }

    private void CreateUser(String email, String pass, String name){
        if (Database.AddUserToDatabase(name,email,pass, UserRole.User, UserStatus.Offline)){
            SendReply(ReplyCodes.UserCreate.getValue());
        }else {
            SendReply(ReplyCodes.EmailInUse.getValue());
        }
    }

    private void SendReply(byte action){
        byte[] buffer = new byte[1];
        buffer[0] = action;
        SendReply(buffer, 1);
    }

    private void SendReply(byte[] message, int messageLength){
        try {
            byte[] buffer = new byte[messageLength + 16];
            System.arraycopy(message, 0, buffer, 0, messageLength);
            System.arraycopy(GenerateAuthKey(), 0, buffer, messageLength, 16);

            OutputStream out = Client.getOutputStream();
            out.write(buffer);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private byte[] GenerateAuthKey(){
        while (true) {
            byte[] buffer = new byte[16];
            new Random().nextBytes(buffer);

            boolean isUnique = true;
            for (User user : users) {
                if (user.AuthKey == buffer) {
                    isUnique = false;
                    break;
                }
            }

            if (!isUnique){
                continue;
            }
            return buffer;
        }
    }

    private int FromByteArray(byte[] bytes) {
        return ((bytes[0] & 0xFF) << 24) |
                ((bytes[1] & 0xFF) << 16) |
                ((bytes[2] & 0xFF) << 8 ) |
                ((bytes[3] & 0xFF) << 0 );
    }

    private byte[] ReadMessage(int messageLength, InputStream input){
        try {
            byte[] buffer = new byte[messageLength];
            int pointer = 0;

            while (pointer < messageLength){
                int count;
                while ((count = input.read(buffer, pointer, 1024)) != -1){
                    pointer += count;
                }

                input = Client.getInputStream();
            }

            return buffer;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private byte[] BuildReplyMessage(byte replyCode, String text){
        byte[] buffer = new byte[text.length() + 1];
        buffer[0] = replyCode;
        byte[] message = text.getBytes(StandardCharsets.UTF_8);

        System.arraycopy(message, 0, buffer, 1, message.length);

        return buffer;
    }
}
