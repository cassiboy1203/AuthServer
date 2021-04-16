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
}
