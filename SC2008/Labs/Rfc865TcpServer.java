import java.net.*;
import java.io.*;

public class Rfc865TcpServer {
    public static void main(String[] argv) {
        ServerSocket serverSocket = null;
        Socket clientSocket = null;
        PrintWriter out = null;
        BufferedReader in = null;

        try {
            // 1. Open ServerSocket to listen on port 17 (well-known port for QOTD)
            serverSocket = new ServerSocket(17); // Port 17 is the QOTD port
            System.out.println("Server is listening on port 17...");

            // 2. Accept incoming client connections
            clientSocket = serverSocket.accept(); // This will block until a client connects

            // 3. Set up input and output streams
            in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            out = new PrintWriter(clientSocket.getOutputStream(), true);

            // 4. Send Quote of the Day (QOTD) to the client
            String quote = "The early bird catches the worm.\n"; // The quote to send
            out.println(quote);  // Send the quote to the client

            // 5. Send a final quote before exiting
            String finalQuote = "A journey of a thousand miles begins with a single step.\n"; // Final quote
            out.println(finalQuote);

            System.out.println("Sent quotes to client.");

        } catch (IOException e) {
            System.err.println("IO Error: " + e.getMessage());
        } finally {
            try {
                // Close streams and sockets
                if (in != null) in.close();
                if (out != null) out.close();
                if (clientSocket != null) clientSocket.close();
                if (serverSocket != null) serverSocket.close();
                System.out.println("Server socket closed.");
            } catch (IOException e) {
                System.err.println("Error closing resources: " + e.getMessage());
            }
        }
    }
}
