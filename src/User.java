import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;

// The status of the user
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

// The role of the user
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
    public UserStatus Status;
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

    // Accepts incoming messages from the user and processes them.
    private void ReceiveMessages() {
        try {
            // gets the input stream
            InputStream input = Client.getInputStream();
            while (true) {
                // setup the variables
                byte[] authKey = new byte[16];
                byte[] actionCode = new byte[1];
                byte[] messageLengthByte = new byte[4];
                int messageLength;
                // reads the header of the tcp message.
                if (input.read(authKey, 0, 16) != 16) continue;
                input.read(actionCode, 0, 1);
                input.read(messageLengthByte, 0, 4);
                // converts the read bytes of the length to a integer
                messageLength = FromByteArray(messageLengthByte);
                byte[] buffer;
                // reads the message send
                buffer = ReadMessage(messageLength, input);
                // if no action was given
                if (buffer == null) {
                    SendReply(ReplyCodes.InvalidArgs.getValue());
                } else {
                    // checks if a valid key was send.
                    if (Arrays.equals(AuthKey, authKey)) {
                        ActionCodes action = ActionCodes.fromValue(actionCode[0]);

                        // converts the messages into a string array.
                        String[] messages = ExtractMessage(buffer);

                        // the actions when not logged in.
                        if (!IsLoggedIn) {
                            // checks what action needs to be executed.
                            switch (action) {
                                case Login:
                                    // if a different status than online has been send.
                                    if (messages.length > 2) {
                                        Status = UserStatus.fromValue(Byte.parseByte(messages[2]));
                                    } else {
                                        Status = UserStatus.Online;
                                    }
                                    // checks if there is a valid login.
                                    CheckLogin(messages[0], messages[1]);
                                    break;
                                case LoginInfo:
                                    // checks if the login key is valid.
                                    CheckLoginInfo(messages[0]);
                                    break;
                                case NewUser:
                                    // creates a new user.
                                    CreateUser(messages[0], messages[1], messages[2]);
                                    break;
                                case None:
                                default:
                                    // if the action send was not valid.
                                    SendReply(ReplyCodes.InvalidAction.getValue());
                            }
                        } else {
                            switch (action) {
                                case None:
                                default:
                                    // if the action send was not valid.
                                    SendReply(ReplyCodes.InvalidAction.getValue());
                            }
                        }
                    } else {
                        // if the authkey was not valid.
                        SendReply(ReplyCodes.InvalidKey.getValue());
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // checks if login is valid.
    private void CheckLogin(String email, String pass) {
        if (Database.CheckLogin(email, pass, this, Client.getRemoteSocketAddress().toString())) {
            // updates the login info in the database.
            int timeStamp = Long.valueOf(System.currentTimeMillis() / 1000L).intValue();
            String loginToken = Database.GenerateLoginToken().get();
            String loginHash = Database.HashPass(loginToken, Integer.toString(timeStamp)).get();
            Database.UpdateLoginInfo(timeStamp, Id, loginHash, Client.getRemoteSocketAddress().toString());
            IsLoggedIn = true;
            SendReply(ReplyCodes.LoginSuccessful.getValue(), Integer.toString(Id), Name, Integer.toString(Role.getValue()), loginToken, Integer.toString(timeStamp));
        } else {
            SendReply(ReplyCodes.LoginFailed.getValue());
        }
        System.gc();
    }

    // checks if the login key is valid.
    private void CheckLoginInfo(String loginInfo) {

    }

    // creates a new user.
    private void CreateUser(String email, String pass, String name) {
        if (Database.AddUserToDatabase(name, email, pass, UserRole.User, UserStatus.Offline)) {
            SendReply(ReplyCodes.UserCreate.getValue());
        } else {
            SendReply(ReplyCodes.EmailInUse.getValue());
        }
        System.gc();
    }

    // sends back the reply code and a new authkey.
    private void SendReply(byte reply) {
        byte[] buffer = new byte[17];
        buffer[0] = reply;
        System.arraycopy(GenerateAuthKey(), 0, buffer, 1, 16);
        SendReply(buffer);
    }

    // sends back the reply code, extra arguments and a new authkey.
    private void SendReply(byte replyCode, String... args) {

        byte[] message = BuildReplyMessage(args);
        byte[] buffer = new byte[message.length + 21];
        byte[] messageLength = ToByteArray(message.length);

        buffer[0] = replyCode;
        System.arraycopy(GenerateAuthKey(), 0, buffer, 1, 16);
        System.arraycopy(messageLength, 0, buffer, 17, 4);
        System.arraycopy(message, 0, buffer, 21, message.length);
        SendReply(buffer);
    }

    // sends the buffer to the user.
    private void SendReply(byte[] buffer) {
        try {
            OutputStream out = Client.getOutputStream();
            out.write(buffer);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    // generates a new authkey.
    private byte[] GenerateAuthKey() {
        while (true) {
            byte[] buffer = new byte[16];
            new Random().nextBytes(buffer);

            // checks if the authkey is not in use.
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
            this.AuthKey = buffer;
            return buffer;
        }
    }

    // converts a byte array to int.
    private int FromByteArray(byte[] bytes) {
        return ((bytes[0] & 0xFF) << 0) |
                ((bytes[1] & 0xFF) << 8) |
                ((bytes[2] & 0xFF) << 16) |
                ((bytes[3] & 0xFF) << 24);
    }

    // converts a int into a byte array.
    byte[] ToByteArray(int value) {
        return new byte[]{
                (byte) (value >> 0),
                (byte) (value >> 8),
                (byte) (value >> 16),
                (byte) (value >> 24)};
    }

    // reads the message buffer.
    private byte[] ReadMessage(int messageLength, InputStream input) {
        try {
            byte[] buffer = new byte[messageLength];
            int pointer = 0;

            // makes sure the entire message is read.
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

    // converts the buffer to a string array.
    private String[] ExtractMessage(byte[] message) {
        int pointer = 0;
        ArrayList<String> values = new ArrayList<String>();
        while (pointer < message.length) {
            String value;
            byte[] length = new byte[4];

            System.arraycopy(message, pointer, length, 0, 4);

            int valueLength = FromByteArray(length);
            byte[] valueArray = new byte[valueLength];

            System.arraycopy(message, pointer + 4, valueArray, 0, valueLength);
            value = new String(valueArray, StandardCharsets.UTF_8);
            values.add(value);

            pointer += valueLength + 4;
        }

        return values.toArray(new String[0]);
    }

    // builds the message to be send back by turning the strings to bytes.
    private byte[] BuildReplyMessage(String... args){
        int count = 0;
        for (String arg: args) {
            count += arg.length() + 4;
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
