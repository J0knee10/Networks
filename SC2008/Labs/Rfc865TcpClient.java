import java.net.*;
import java.io.*;

public class Rfc865TcpClient {
    public static void main(String[] argv) {
        Socket socket = null;
        PrintWriter out = null;
        BufferedReader in = null;

        try {
            // 1. Connect to the server (use the server's IP address or hostname)
            socket = new Socket("localhost", 17); // IP address and port 17 (QOTD)

            // 2. Set up input and output streams
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);

            // 3. Receive the Quote of the Day from the server
            String quote = in.readLine();  // Read the first line (QOTD)
            System.out.println("Received Quote: " + quote);

            // 4. Receive the final quote before the server shuts down
            String finalQuote = in.readLine();  // Read the final quote
            System.out.println("Received Final Quote: " + finalQuote);

        } catch (IOException e) {
            System.err.println("IO Error: " + e.getMessage());
        } finally {
            try {
                // Close streams and socket
                if (in != null) in.close();
                if (out != null) out.close();
                if (socket != null) socket.close();
                System.out.println("Socket closed.");
            } catch (IOException e) {
                System.err.println("Error closing resources: " + e.getMessage());
            }
        }
    }
}
