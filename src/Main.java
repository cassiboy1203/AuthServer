public class Main {
    public static void main(String[] args) throws InterruptedException {
        Thread t = new Thread(AuthServer::OpenServer);
        t.start();
        Main object = new Main();
        object.waitMethod();
    }

    private synchronized void waitMethod() throws InterruptedException {
        while (true){
            this.wait(2000);
        }
    }
}
