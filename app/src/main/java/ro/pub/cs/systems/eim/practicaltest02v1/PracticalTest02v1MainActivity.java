package ro.pub.cs.systems.eim.practicaltest02v1;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

public class PracticalTest02v1MainActivity extends AppCompatActivity {

    private static final String TAG = "PT02v1";

    // UI (Ex.2)
    private EditText portEditText;
    private EditText prefixEditText;
    private Button startServerButton, stopServerButton, requestButton;
    private TextView resultTextView;

    // Server
    private ServerThread serverThread;

    // UI Handler
    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_practical_test02v1_main);

        //Ex.2:

        portEditText = findViewById(R.id.portEditText);
        prefixEditText = findViewById(R.id.prefixEditText);
        startServerButton = findViewById(R.id.startServerButton);
        stopServerButton = findViewById(R.id.stopServerButton);
        requestButton = findViewById(R.id.requestButton);
        resultTextView = findViewById(R.id.resultTextView);


        startServerButton.setOnClickListener(v -> {
            int port = parsePort();


            serverThread = new ServerThread(port);
            serverThread.start();
            toast("Server pornit pe port " + port);
        });

        stopServerButton.setOnClickListener(v -> {
            stopServer();
            toast("Server oprit");
        });

        requestButton.setOnClickListener(v -> {
            int port = parsePort();
            String prefix = prefixEditText.getText().toString();

            new ClientThread("127.0.0.1", port, prefix).start();
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopServer();
    }

    private void stopServer() {
        if (serverThread != null) {
            serverThread.stopServer();
            serverThread = null;
        }
    }

    private int parsePort() {
        try {
            int p = Integer.parseInt(portEditText.getText().toString().trim());
            Log.d(TAG, "S2: parsePortOK " + p);
            return p;
        } catch (Exception e) {
            Log.w(TAG, "S2: parsePort FAIL" + e.getMessage());
            return -1;
        }
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    // SERVER


    private class ServerThread extends Thread {
        private final int port;
        private volatile boolean running = true;
        private ServerSocket serverSocket;

        ServerThread(int port) {
            this.port = port;
        }

        void stopServer() {
            running = false;
            try {
                if (serverSocket != null) serverSocket.close();
            } catch (Exception e) {
                Log.w(TAG, "Eroare la close serverSocket: " + e.getMessage());
            }
        }

        @Override
        public void run() {
            try {
                serverSocket = new ServerSocket(port);
                while (running) {
                    Socket client = serverSocket.accept();
                    Log.i(TAG, "3c: Client conectat de la " + client.getInetAddress() + ":" + client.getPort());

                    new CommunicationThread(client).start();
                    Log.i(TAG, "3c: Pornit CommunicationThread pentru client");
                }
            } catch (Exception e) {
                Log.e(TAG, "3c: ServerThread error: " + e.getMessage(), e);
            } finally {
                try {
                    if (serverSocket != null) serverSocket.close();
                    Log.i(TAG, "3c: ServerSocket inchis");
                } catch (Exception ignored) {}
            }
        }
    }

    private class CommunicationThread extends Thread {
        private final Socket socket;

        CommunicationThread(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            Log.i(TAG, "S3: CommunicationThread START pentru client " + socket.getInetAddress() + ":" + socket.getPort());

            try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                 PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true)) {

                Log.i(TAG, "3c: Citesc prefix de la client  ...");
                String prefix = in.readLine();
                if (prefix == null) prefix = "";
                prefix = prefix.trim();
                Log.i(TAG, "3c: Prefix primit=\"" + prefix + "\"");

                // Ex.3a:
                String raw = fetchAutocompleteRaw(prefix);
                Log.d(TAG, "3a: Raspuns brut " +
                        (raw.length() > 200 ? raw.substring(0, 200) : raw));

                //Ex.3b:
                Log.i(TAG, "3b: Parsez sugestiile din raspuns");
                List<String> suggestions = parseSuggestions(raw);
                if (!suggestions.isEmpty()) {
                    Log.d(TAG, "3b: Primele sugestii=" + suggestions.subList(0, Math.min(5, suggestions.size())));
                }

                //  Ex.3c: trimiterea informatiilor catre client
                String formatted = formatForClient(suggestions);
                Log.i(TAG, "3c: Trimit catre client format \"" +
                        (formatted.length() > 200 ? formatted.substring(0, 200) : formatted).replace("\n","\\n") + "\"");

                out.print(formatted);
                out.flush();

            } catch (Exception e) {
                Log.e(TAG, "error: " + e.getMessage(), e);
            } finally {
                try {
                    socket.close();

                } catch (Exception ignored) {}
            }
        }
    }

    // CLIENT

    private class ClientThread extends Thread {
        private final String host;
        private final int port;
        private final String prefix;

        ClientThread(String host, int port, String prefix) {
            this.host = host;
            this.port = port;
            this.prefix = prefix == null ? "" : prefix;
        }

        @Override
        public void run() {
            Log.i(TAG, "CLIENT: incerc conectare la " + host + ":" + port);

            try (Socket socket = new Socket(host, port);
                 PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);
                 BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

                out.println(prefix);
                out.flush();

                Log.i(TAG, "CLIENT: citesc raspuns de la server pana se inchide ");
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = in.readLine()) != null) {
                    sb.append(line).append("\n");
                }
                String finalText = sb.toString().trim();
                Log.i(TAG, "CLIENT: raspuns primit, lungime=" + finalText.length());
                Log.d(TAG, "CLIENT: raspuns (primele 200)=" + (finalText.length() > 200 ? finalText.substring(0, 200) : finalText));

                uiHandler.post(() -> {
                    resultTextView.setText(finalText);
                    Toast.makeText(PracticalTest02v1MainActivity.this, finalText, Toast.LENGTH_LONG).show();
                });

            } catch (Exception e) {
                Log.e(TAG, "CLIENT: error: " + e.getMessage(), e);
                uiHandler.post(() -> toast("Eroare client: " + e.getMessage()));
            }
        }
    }

     // Ex.3a:primirea informatiilor
    private String fetchAutocompleteRaw(String prefix) throws Exception {
        String q = URLEncoder.encode(prefix, "UTF-8");
        String urlStr = "https://www.google.com/complete/search?client=chrome&q=" + q;
        Log.d(TAG, "S3a: URL=" + urlStr);

        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(8000);
        conn.setRequestProperty("User-Agent", "Mozilla/5.0");

        int code = conn.getResponseCode();
        Log.i(TAG, " HTTP responseCode=" + code);

        try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            Log.i(TAG, "S3a: HTTp citit, chars=" + sb.length());
            return sb.toString();
        } finally {
            conn.disconnect();
        }
    }

     // Ex.3b: Parsare sugestii

    private List<String> parseSuggestions(String raw) {
        List<String> out = new ArrayList<>();


        try {
            JSONArray root = new JSONArray(raw);
            JSONArray arr = root.getJSONArray(1);
            for (int i = 0; i < arr.length(); i++) {
                String s = arr.optString(i, "").trim();
                if (!s.isEmpty()) out.add(s);
            }
            if (!out.isEmpty()) return out;
        } catch (Exception e) {
            Log.w(TAG, " JSON esuat -> " + e.getMessage());
        }

        Log.d(TAG, "S3b: Fallback indexOf(\"name\")");
        final String key = "\"name\":\"";
        int idx = 0;

        while (true) {
            int start = raw.indexOf(key, idx);
            if (start < 0) break;
            start += key.length();
            int end = raw.indexOf("\"", start);
            if (end < 0) break;

            String s = raw.substring(start, end).trim();
            if (!s.isEmpty()) out.add(s);

            idx = end + 1;
        }

        return out;
    }

    // cum pot testa functionalitatea suportata de server folsind un utilitar in linia de comanda pe telefon de ex telnet sau busybox nc. de ex b

     // Ex.3c:
    private String formatForClient(List<String> suggestions) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < suggestions.size(); i++) {
            sb.append(suggestions.get(i));
            if (i != suggestions.size() - 1) sb.append(",");
        }
        sb.append("\n");
        return sb.toString();
    }
}