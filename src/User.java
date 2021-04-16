import com.sun.source.tree.IfTree;

import java.net.Socket;

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

    public User(Socket client){
        this.Client = client;
    }
    public User(String email, String pass, Socket client){
        this.Client = client;
        CheckLogin(email, pass);
    }
    public User(String loginInfo, Socket client){
        this.Client = client;
        CheckLoginInfo(loginInfo);
    }
    public User(String email, String name, String pass, String image, Socket socket){
        if (Database.AddUserToDatabase(name, email, pass, UserRole.User, UserStatus.Online, !image.equals("null") ? image : null)){

        }
    }

    public void CheckLogin(String email, String pass){
        if (Database.CheckLogin(email, pass, this, Client.getRemoteSocketAddress().toString())){
            IsLoggedIn = true;
            System.out.println("User logged in.");
        }
    }

    public void CheckLoginInfo(String loginInfo){

    }
}
