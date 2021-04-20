import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;

enum UserStatus {
    Online((byte) 0),
    Offline((byte) 1),
    Idle((byte) 2),
    Dnd((byte) 3),
    Invisible((byte) 4);

    private byte value;

    UserStatus(byte value) {
        this.value = value;
    }

    public static UserStatus fromValue(byte value) {
        for (UserStatus status : values()) {
            if (status.getValue() == value) {
                return status;
            }
        }
        return null;
    }

    public int getValue() {
        return value;
    }
}

enum UserRole {
    User((byte) 0),
    Mod((byte) 29),
    Dev((byte) 30);

    public byte value;

    UserRole(byte value) {
        this.value = value;
    }

    public static UserRole fromValue(byte value) {
        for (UserRole role : values()) {
            if (role.getValue() == value) {
                return role;
            }
        }
        return null;
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

    public User(Socket client, ArrayList<User> users) {
        this.Client = client;
        AuthServer.users.add(this);
        SendReply(ReplyCodes.ConnectionSuccessful.getValue());

        this.users = users;

        ReceiveMessages();
    }

    private void ReceiveMessages() {
        try {
            InputStream input = Client.getInputStream();
            while (true) {
                byte[] authKey = new byte[16];
                byte[] actionCode = new byte[1];
                byte[] messageLengthByte = new byte[4];
                int messageLength;
                if (input.read(authKey, 0, 16) != 16) continue;
                input.read(actionCode, 0, 1);
                input.read(messageLengthByte, 0, 4);
                messageLength = FromByteArray(messageLengthByte);
                byte[] buffer;
                buffer = ReadMessage(messageLength, input);
                if (buffer == null) {
                    SendReply(ReplyCodes.InvalidArgs.getValue());
                } else {

                    if (Arrays.equals(AuthKey, authKey)) {
                        ActionCodes action = ActionCodes.fromValue(actionCode[0]);

                        String[] messages = ExtractMessage(buffer);

                        if (!IsLoggedIn) {
                            switch (action) {
                                case Login:
                                    if (messages.length > 2) {
                                        status = UserStatus.fromValue(Byte.parseByte(messages[2]));
                                    } else {
                                        status = UserStatus.Online;
                                    }
                                    CheckLogin(messages[0], messages[1]);
                                    break;
                                case LoginInfo:
                                    CheckLoginInfo(messages[0]);
                                    break;
                                case NewUser:
                                    CreateUser(messages[0], messages[1], messages[2]);
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
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void CheckLogin(String email, String pass) {
        if (Database.CheckLogin(email, pass, this, Client.getRemoteSocketAddress().toString())) {
            IsLoggedIn = true;
            SendReply(ReplyCodes.LoginSuccessful.getValue());
        } else {
            SendReply(ReplyCodes.LoginFailed.getValue());
        }
    }

    private void CheckLoginInfo(String loginInfo) {

    }

    private void CreateUser(String email, String pass, String name) {
        if (Database.AddUserToDatabase(name, email, pass, UserRole.User, UserStatus.Offline)) {
            SendReply(ReplyCodes.UserCreate.getValue());
        } else {
            SendReply(ReplyCodes.EmailInUse.getValue());
        }
    }

    private void SendReply(byte action) {
        byte[] buffer = new byte[17];
        buffer[0] = action;
        System.arraycopy(GenerateAuthKey(), 0, buffer, 1, 16);
        SendReply(buffer);
    }

    private void SendReply(byte replyCode, String... args) {

        byte[] message = BuildReplyMessage(args);
        byte[] buffer = new byte[message.length + 21];

        System.arraycopy(message.length, 0, buffer, 17, 4);
        System.arraycopy(message, 0, buffer, 22, message.length);
        System.arraycopy(GenerateAuthKey(), 0, buffer, 1, 16);
        SendReply(buffer);
    }

    private void SendReply(byte[] buffer) {
        try {
            OutputStream out = Client.getOutputStream();
            out.write(buffer);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private byte[] GenerateAuthKey() {
        while (true) {
            byte[] buffer = new byte[16];
            new Random().nextBytes(buffer);

            boolean isUnique = true;
            if (users != null) {
                for (User user : users) {
                    if (user.AuthKey == buffer) {
                        isUnique = false;
                        break;
                    }
                }
            }

            if (!isUnique) {
                continue;
            }
            return buffer;
        }
    }

    private int FromByteArray(byte[] bytes) {
        return ((bytes[0] & 0xFF) << 0) |
                ((bytes[1] & 0xFF) << 8) |
                ((bytes[2] & 0xFF) << 16) |
                ((bytes[3] & 0xFF) << 24);
    }

    byte[] ToByteArray(int value) {
        return new byte[]{
                (byte) (value >> 0),
                (byte) (value >> 8),
                (byte) (value >> 16),
                (byte) (value >> 24)};
    }

    private byte[] ReadMessage(int messageLength, InputStream input) {
        try {
            byte[] buffer = new byte[messageLength];
            int pointer = 0;

            int count;
            while (pointer < messageLength) {
                count = input.read(buffer, pointer, messageLength - pointer < 1024 ? messageLength : 1024);
                pointer += count;
            }

            return buffer;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private String[] ExtractMessage(byte[] message) {
        int pointer = 0;
        ArrayList<String> values = null;
        while (pointer < message.length) {
            String value;
            byte[] length = new byte[4];

            System.arraycopy(message, pointer, length, 0, 4);

            int valueLength = FromByteArray(length);
            byte[] valueArray = new byte[valueLength];

            System.arraycopy(message, pointer + 4, valueArray, 0, valueLength);
            value = new String(valueArray, StandardCharsets.UTF_8);
            values.add(value);

            pointer += valueLength;
        }

        return (String[]) values.toArray();
    }

    private byte[] BuildReplyMessage(String... args){
        int count = 0;
        for (String arg: args) {
            count += arg.length();
        }

        byte[] buffer = new byte[count];
        int pointer = 0;
        for (String arg : args){
            byte[] length = ToByteArray(arg.length());
            byte[] message = arg.getBytes(StandardCharsets.UTF_8);
            System.arraycopy(length, 0, buffer, pointer, 4);
            System.arraycopy(message, 0, buffer, pointer + 4, message.length);

            pointer += message.length + 4;
        }

        return buffer;
    }
}
