public class Main {
    public static void main(String[] args) throws InterruptedException {
        // starts the application.
        //Database.AddUserToDatabase("Dev1", "dev1@chatserver.com", "Dev1Pass", UserRole.Dev, UserStatus.Offline);
        Thread t = new Thread(AuthServer::OpenServer);
        t.start();
        while (true) Thread.sleep(100000);
    }
}
