import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;
import java.util.Random;

// The status of the user
enum UserStatus {
    Online((byte) 0),
    Offline((byte) 1),
    Idle((byte) 2),
    Dnd((byte) 3),
    Invisible((byte) 4);

    private final byte value;

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

    public byte getValue() {
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

    public byte getValue() {
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
    public String FriendCode;
    public String UserToken;
    public int LastUpdateTime;

    public Socket Client;
    public byte[] AuthKey;

    private final ArrayList<User> users;

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
                byte[] buffer;
                // reads the header of the tcp message.
                if (input.read(authKey, 0, 16) != 16) continue;
                input.read(actionCode, 0, 1);
                input.read(messageLengthByte, 0, 4);
                // converts the read bytes of the length to a integer
                messageLength = FromByteArray(messageLengthByte);
                // reads the message send
                if (messageLength > 0) buffer = ReadMessage(messageLength, input);
                else buffer = null;

                // if no action was given
                if (buffer == null && messageLength > 0) {
                    SendReply(ReplyCodes.InvalidArgs.getValue());
                } else {
                    // checks if a valid key was send.
                    if (Arrays.equals(AuthKey, authKey)) {
                        ActionCodes action = ActionCodes.fromValue(actionCode[0]);

                        // converts the messages into a string array.
                        assert buffer != null;
                        String[] messages = null;
                        if (buffer != null) messages = ExtractMessage(buffer);

                        // the actions when not logged in.
                        if (!IsLoggedIn) {
                            // checks what action needs to be executed.
                            switch (action) {
                                case Login -> {
                                    // if a different status than online has been send.
                                    Status = messages.length > 2 ? UserStatus.fromValue(Byte.parseByte(messages[2])) : UserStatus.Online;
                                    // checks if there is a valid login.
                                    CheckLogin(messages[0], messages[1]);
                                }
                                case LoginInfo ->
                                        // checks if the login key is valid.
                                        {
                                            Status = messages.length > 2 ? UserStatus.fromValue(Byte.parseByte(messages[2])) : UserStatus.Online;
                                            CheckLoginInfo(messages[0], messages[1]);
                                        }
                                case NewUser ->
                                        // creates a new user.
                                        CreateUser(messages[0], messages[1], messages[2]);
                                case Disconnect -> {
                                    SendReply(ReplyCodes.Confirm.getValue());
                                    return;
                                }
                                default ->
                                        // if the action send was not valid.
                                        SendReply(ReplyCodes.InvalidAction.getValue());
                            }
                        } else {
                            switch (action) {
                                case Disconnect -> {
                                    SendReply(ReplyCodes.Confirm.getValue());
                                    return;
                                }
                                case Logout -> {

                                }
                                case AddFriend -> {
                                    AddFriend(messages[0], messages[1]);
                                }
                                case GetFriends, GetFriendRequest -> {
                                    try {
                                        int lastUpdate = Integer.parseInt(messages[0]);
                                        GetFriends(action, lastUpdate);
                                    } catch (NumberFormatException ex){
                                        ex.printStackTrace();
                                        SendReply(ReplyCodes.InvalidArgs.getValue());
                                    }
                                }
                                case AcceptRequest, RejectRequest -> {
                                    UpdateRequest(action, messages[0]);
                                }
                                case GetBlockedUsers -> {
                                    try {
                                        int lastUpdate = Integer.parseInt(messages[0]);
                                        GetBlockedUsers(lastUpdate);
                                    } catch (NumberFormatException ex){
                                        ex.printStackTrace();
                                        SendReply(ReplyCodes.InvalidArgs.getValue());
                                    }
                                }
                                case BlockUser -> {
                                    BlockUser(messages[0]);
                                }
                                case UnblockUser -> {
                                    UnBlockUser(messages[0]);
                                }
                                case SendPrivateMessage -> {
                                    SendPrivateMessage(messages[0], messages[1]);
                                }
                                default ->
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
        } finally {
            try {
                if (Client != null) Client.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // checks if login is valid.
    private void CheckLogin(String email, String pass) {
        if (Database.CheckLogin(email, pass, this)) {
            // updates the login info in the database.
            int timeStamp = Long.valueOf(System.currentTimeMillis() / 1000L).intValue();
            String loginToken = Database.GenerateLoginToken().get();
            String loginHash = Database.HashPass(loginToken, Integer.toString(timeStamp)).get();
            String ip = (((InetSocketAddress) Client.getRemoteSocketAddress()).getAddress()).toString().replace("/","");
            Database.UpdateLoginInfo(timeStamp, Id, loginHash, ip);
            IsLoggedIn = true;
            SendReply(ReplyCodes.LoginSuccessful.getValue(), FriendCode, Name, Integer.toString(Role.getValue()), loginToken, UserToken);
        } else {
            SendReply(ReplyCodes.LoginFailed.getValue());
        }
        System.gc();
    }

    // checks if the login key is valid.
    private void CheckLoginInfo(String loginToken, String userToken) {
        String ip = (((InetSocketAddress) Client.getRemoteSocketAddress()).getAddress()).toString().replace("/","");
        if (Database.CheckLoginInfo(loginToken, userToken, this, ip)) {
            IsLoggedIn = true;
            SendReply(ReplyCodes.LoginSuccessful.getValue(), userToken, Name, Email, Integer.toString(Role.getValue()), FriendCode) ;
        } else {
            SendReply(ReplyCodes.LoginFailed.getValue());
        }
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

    private void AddFriend(String userName, String friendCode){
        int out = Database.SendFriendRequest(Id, userName, friendCode);
        if (out == 1){
            SendReply(ReplyCodes.FriendRequestSend.getValue());
        } else if (out == 0){
            SendReply(ReplyCodes.FriendRequestExists.getValue());
        } else {
            SendReply(ReplyCodes.UserNotFound.getValue());
        }
    }

    private void GetFriends(ActionCodes action, int lastUpdateTime){
        ArrayList<Friend> friends = null;
        if (action == ActionCodes.GetFriends) friends = Database.GetFriends(Id, lastUpdateTime);
        else if (action == ActionCodes.GetFriendRequest) friends = Database.GetFriendRequests(Id, lastUpdateTime);

        if (friends != null){
            ArrayList<byte[]> buffer = new ArrayList<>();
            for (Friend friend : friends){
                if (action == ActionCodes.GetFriends) buffer.add(BuildReplyMessage(friend.token, friend.Name, Byte.toString(friend.status.getValue())));
                else buffer.add(BuildReplyMessage(friend.token, friend.Name));
            }
            byte[] message = BuildExtendedReplyMessage(buffer);
            SendReply(ReplyCodes.FriendsFound.getValue(), message);
        } else {
            SendReply(ReplyCodes.FriendsNotFound.getValue());
        }
    }

    private void GetBlockedUsers(int lastUpdateTime){
        ArrayList<Friend> blockedUsers = Database.GetBlockedUsers(Id, lastUpdateTime);

        if (blockedUsers != null){
            ArrayList<byte[]> buffer = new ArrayList<>();
            for (Friend blockedUser: blockedUsers){
                buffer.add(BuildReplyMessage(blockedUser.token, blockedUser.Name));
            }
            byte[] message = BuildExtendedReplyMessage(buffer);
            SendReply(ReplyCodes.FriendsFound.getValue(), message);
        } else {
            SendReply(ReplyCodes.FriendsNotFound.getValue());
        }
    }

    private void UpdateRequest(ActionCodes action, String userToken){
        if (Database.UpdateRequestStatus(action, userToken, Id)){
            SendReply(ReplyCodes.Confirm.getValue());
        } else {
            SendReply(ReplyCodes.InvalidArgs.getValue());
        }
    }

    private void BlockUser(String userToken){
        if (Database.BlockUser(Id, userToken)){
            SendReply(ReplyCodes.Confirm.getValue());
        } else {
            SendReply(ReplyCodes.FriendRequestExists.getValue());
        }
    }

    private void UnBlockUser(String userToken){
        if (Database.UnBlockUser(Id, userToken)){
            SendReply(ReplyCodes.Confirm.getValue());
        } else {
            SendReply(ReplyCodes.InvalidArgs.getValue());
        }
    }

    private void SendPrivateMessage(String text, String userToken){
        if (Database.SaveMessage(text, true, 0, Id, userToken)){
            SendReply(ReplyCodes.MessageReceived.getValue());
        } else {
            SendReply(ReplyCodes.InvalidArgs.getValue());
        }
    }

    // sends back the reply code and a new authkey.
    private void SendReply(byte reply) {
        byte[] buffer = new byte[17];
        buffer[0] = reply;
        System.arraycopy(GenerateAuthKey(), 0, buffer, 1, 16);
        SendReply(buffer);
    }

    // converts the arguments into bytes
    private void SendReply(byte replyCode, String... args) {
        byte[] message = BuildReplyMessage(args);
        SendReply(replyCode, message);
    }

    // sends back the reply code, message and a new authkey.
    private void SendReply(byte reply, byte[] message){
        byte[] buffer = new byte[message.length + 21];
        byte[] messageLength = ToByteArray(message.length);

        buffer[0] = reply;
        System.arraycopy(GenerateAuthKey(), 0, buffer, 1, 16);
        System.arraycopy(messageLength,0,buffer,17,4);
        System.arraycopy(message,0,buffer,21,message.length);

        SendReply(buffer);
    }

    // sends the buffer to the user.
    private void SendReply(byte[] buffer) {
        try {
            OutputStream out = Client.getOutputStream();
            if (buffer.length > 1024){
                int pointer = 0;
                while (pointer < buffer.length){
                    int messageLength = buffer.length - pointer > 1024 ? 1024 : buffer.length - pointer;
                    byte[] splitBuffer = new byte[messageLength];
                    System.arraycopy(buffer, pointer, splitBuffer, 0, messageLength);
                    out.write(splitBuffer);
                    pointer += messageLength;
                }
            }
            else out.write(buffer);
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
        return ((bytes[0] & 0xFF)) |
                ((bytes[1] & 0xFF) << 8) |
                ((bytes[2] & 0xFF) << 16) |
                ((bytes[3] & 0xFF) << 24);
    }

    // converts a int into a byte array.
    byte[] ToByteArray(int value) {
        return new byte[]{
                (byte) (value),
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
        ArrayList<String> values = new ArrayList<>();
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
    private byte[] BuildReplyMessage(String... args) {
        int count = 0;
        for (String arg : args) {
            count += arg.length() + 4;
        }

        byte[] buffer = new byte[count];
        int pointer = 0;
        for (String arg : args) {
            byte[] length = ToByteArray(arg.length());
            byte[] message = arg.getBytes(StandardCharsets.UTF_8);
            System.arraycopy(length, 0, buffer, pointer, 4);
            System.arraycopy(message, 0, buffer, pointer + 4, message.length);

            pointer += message.length + 4;
        }

        return buffer;
    }

    private byte[] BuildExtendedReplyMessage(ArrayList<byte[]> messages){
        int count = 0;
        for (byte[] message : messages){
            count += message.length + 4;
        }

        byte[] buffer = new byte[count];
        int pointer = 0;
        for (byte[] message : messages){
            byte[] length = ToByteArray(message.length);
            System.arraycopy(length, 0, buffer, pointer, 4);
            System.arraycopy(message, 0, buffer, pointer + 4, message.length);

            pointer += message.length + 4;
        }

        return buffer;
    }
}

class Friend{
    String token;
    String Name;
    String image;
    UserStatus status;
}

class Message{

}
