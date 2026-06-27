package com.mindmate;

import com.mindmate.api.ApiServer;

import java.net.BindException;

public class Main {
    public static void main(String[] args) throws Exception {
        int preferredPort = 9090;
        String portValue = System.getenv("PORT");
        if (portValue != null && !portValue.isBlank()) {
            preferredPort = Integer.parseInt(portValue);
        } else if (args.length > 0 && !args[0].isBlank()) {
            preferredPort = Integer.parseInt(args[0]);
        }

        ApiServer server = null;
        int selectedPort = preferredPort;
        for (int port = preferredPort; port <= preferredPort + 9; port++) {
            try {
                server = new ApiServer(port);
                selectedPort = port;
                break;
            } catch (BindException exception) {
                System.out.println("Port " + port + " is already in use, trying " + (port + 1) + "...");
            }
        }

        if (server == null) {
            throw new BindException("No free backend port found from " + preferredPort + " to " + (preferredPort + 9));
        }

        server.start();
        System.out.println("MindMate backend running at http://localhost:" + selectedPort);
    }
}
