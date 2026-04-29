import com.sun.net.httpserver.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.file.*;
import java.util.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Server {
    static class Transaction {
        String type;
        double amount;
        String date;

        Transaction(String type, double amount) {
            this.type = type;
            this.amount = amount;
            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            this.date = LocalDateTime.now().format(dtf);
        }
    }

    static class UserAccount {
        String accountId;
        String pin;
        double balance;
        List<Transaction> history;
        List<String> contacts;
        boolean isFrozen;

        UserAccount(String accountId, String pin) {
            this.accountId = accountId;
            this.pin = pin;
            this.balance = 0.0;
            this.history = new ArrayList<>();
            this.contacts = new ArrayList<>();
            this.isFrozen = false;
        }
    }

    private static Map<String, UserAccount> database = new HashMap<>();
    private static final String DATA_FILE = "gob_bank_data.txt";

    private static synchronized void saveData() {
        try (PrintWriter out = new PrintWriter(new FileWriter(DATA_FILE))) {
            for (UserAccount user : database.values()) {
                out.println("[USER]");
                out.println("ID=" + user.accountId);
                out.println("PIN=" + user.pin);
                out.println("BALANCE=" + user.balance);
                out.println("FROZEN=" + user.isFrozen);
                out.println("CONTACTS=" + String.join(",", user.contacts));
                out.println("[HISTORY]");
                for (int i = user.history.size() - 1; i >= 0; i--) {
                    Transaction t = user.history.get(i);
                    out.println(t.type + "|" + t.amount + "|" + t.date);
                }
                out.println("[ENDUSER]");
            }
        } catch (IOException e) {
            System.err.println("Failed to save data: " + e.getMessage());
        }
    }

    private static synchronized void loadData() {
        File file = new File(DATA_FILE);
        if (!file.exists()) {
            database.put("1001", new UserAccount("1001", "1234"));
            return;
        }
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            UserAccount currentUser = null;
            boolean readingHistory = false;

            while ((line = br.readLine()) != null) {
                if (line.equals("[USER]")) {
                    currentUser = new UserAccount("", "");
                    readingHistory = false;
                } else if (line.equals("[HISTORY]")) {
                    readingHistory = true;
                } else if (line.equals("[ENDUSER]")) {
                    if (currentUser != null && !currentUser.accountId.isEmpty()) {
                        Collections.reverse(currentUser.history);
                        database.put(currentUser.accountId, currentUser);
                    }
                } else if (readingHistory) {
                    String[] parts = line.split("\\|");
                    if (parts.length == 3 && currentUser != null) {
                        Transaction t = new Transaction(parts[0], Double.parseDouble(parts[1]));
                        t.date = parts[2];
                        currentUser.history.add(t);
                    }
                } else if (currentUser != null) {
                    if (line.startsWith("ID="))
                        currentUser.accountId = line.substring(3);
                    else if (line.startsWith("PIN="))
                        currentUser.pin = line.substring(4);
                    else if (line.startsWith("BALANCE="))
                        currentUser.balance = Double.parseDouble(line.substring(8));
                    else if (line.startsWith("FROZEN="))
                        currentUser.isFrozen = Boolean.parseBoolean(line.substring(7));
                    else if (line.startsWith("CONTACTS=")) {
                        String contacts = line.substring(9);
                        if (!contacts.isEmpty()) {
                            currentUser.contacts.addAll(Arrays.asList(contacts.split(",")));
                        }
                    }
                }
            }
            System.out.println("Loaded " + database.size() + " accounts from database.");
        } catch (Exception e) {
            System.err.println("Failed to load data: " + e.getMessage());
            if (database.isEmpty())
                database.put("1001", new UserAccount("1001", "1234"));
        }
    }

    private static String decrypt(String encryptedToken) {
        try {
            byte[] decodedBytes = Base64.getDecoder().decode(encryptedToken);
            String decodedString = new String(decodedBytes, "UTF-8");
            StringBuilder decrypted = new StringBuilder();
            for (char c : decodedString.toCharArray()) {
                decrypted.append((char) (c - 1));
            }
            return decrypted.toString();
        } catch (Exception e) {
            return "";
        }
    }

    public static void main(String[] args) throws IOException {
        loadData();
        HttpServer server = HttpServer.create(new InetSocketAddress(8081), 0);
        server.createContext("/", new StaticFileHandler());

        server.createContext("/api/signup", exchange -> {
            if ("POST".equals(exchange.getRequestMethod())) {
                Map<String, String> data = parseQuery(getRequestBody(exchange));
                String[] parts = decrypt(data.get("token")).split(":");
                if (parts.length == 2) {
                    if (database.containsKey(parts[0])) {
                        sendResponse(exchange, 400, "{\"success\": false, \"message\": \"Account already exists!\"}");
                    } else {
                        database.put(parts[0], new UserAccount(parts[0], parts[1]));
                        saveData();
                        sendResponse(exchange, 200, "{\"success\": true}");
                    }
                } else {
                    sendResponse(exchange, 400, "{\"success\": false, \"message\": \"Invalid registration payload.\"}");
                }
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        });

        server.createContext("/api/login", exchange -> {
            if ("POST".equals(exchange.getRequestMethod())) {
                Map<String, String> data = parseQuery(getRequestBody(exchange));
                String[] parts = decrypt(data.get("token")).split(":");
                if (parts.length == 2) {
                    UserAccount user = database.get(parts[0]);
                    if (user != null && user.pin.equals(parts[1])) {
                        sendResponse(exchange, 200, "{\"success\": true}");
                        return;
                    }
                }
                sendResponse(exchange, 401, "{\"success\": false, \"message\": \"Invalid Account or PIN.\"}");
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        });

        server.createContext("/api/userinfo", exchange -> {
            Map<String, String> query = parseQuery(exchange.getRequestURI().getQuery());
            UserAccount user = database.get(query.get("account"));
            if (user != null) {
                StringBuilder json = new StringBuilder("{\"balance\":").append(user.balance)
                        .append(",\"isFrozen\":").append(user.isFrozen)
                        .append(",\"contacts\":[");
                for (int i = 0; i < user.contacts.size(); i++) {
                    json.append("\"").append(user.contacts.get(i)).append("\"");
                    if (i < user.contacts.size() - 1)
                        json.append(",");
                }
                json.append("],\"history\":[");
                for (int i = 0; i < user.history.size(); i++) {
                    Transaction t = user.history.get(i);
                    json.append(String.format("{\"type\":\"%s\", \"amount\":%.2f, \"date\":\"%s\"}", t.type, t.amount,
                            t.date));
                    if (i < user.history.size() - 1)
                        json.append(",");
                }
                json.append("]}");
                sendResponse(exchange, 200, json.toString());
            } else {
                sendResponse(exchange, 404, "{\"error\": \"User not found\"}");
            }
        });

        server.createContext("/api/addcontact", exchange -> {
            if ("POST".equals(exchange.getRequestMethod())) {
                Map<String, String> data = parseQuery(getRequestBody(exchange));
                String acc = data.get("account"), contactAcc = data.get("contact");
                UserAccount user = database.get(acc);
                if (user == null || !database.containsKey(contactAcc)) {
                    sendResponse(exchange, 404, "{\"success\": false, \"message\": \"User or Contact not found\"}");
                    return;
                }
                if (acc.equals(contactAcc)) {
                    sendResponse(exchange, 400, "{\"success\": false, \"message\": \"Cannot add yourself\"}");
                    return;
                }
                if (!user.contacts.contains(contactAcc)) {
                    user.contacts.add(contactAcc);
                    saveData();
                }
                sendResponse(exchange, 200, "{\"success\": true}");
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        });

        server.createContext("/api/transaction", exchange -> {
            if ("POST".equals(exchange.getRequestMethod())) {
                Map<String, String> data = parseQuery(getRequestBody(exchange));
                String acc = data.get("account"), type = data.get("type");
                UserAccount user = database.get(acc);
                if (user == null) {
                    sendResponse(exchange, 404, "{\"success\": false, \"message\": \"Account not found\"}");
                    return;
                }

                if (user.isFrozen) {
                    sendResponse(exchange, 403,
                            "{\"success\": false, \"message\": \"🚨 FRAUD ALERT: Account frozen due to suspicious activity!\"}");
                    return;
                }

                double amount;
                try {
                    amount = Double.parseDouble(data.get("amount"));
                } catch (Exception e) {
                    sendResponse(exchange, 400, "{\"success\": false, \"message\": \"Invalid amount\"}");
                    return;
                }

                if (amount <= 0) {
                    sendResponse(exchange, 400, "{\"success\": false, \"message\": \"Amount must be > 0\"}");
                    return;
                }

                // FRAUD DETECTION ENGINE
                if (!"deposit".equalsIgnoreCase(type)) {
                    // Rule 1: High Value Threshold
                    if (amount > 10000) {
                        user.isFrozen = true;
                        saveData();
                        sendResponse(exchange, 403,
                                "{\"success\": false, \"message\": \"🚨 FRAUD ALERT: Unusually large transaction blocked. Account Frozen!\"}");
                        return;
                    }

                    // Rule 2: Transaction Velocity (Too fast)
                    long recentTxCount = user.history.stream().filter(t -> {
                        try {
                            LocalDateTime txDate = LocalDateTime.parse(t.date,
                                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                            return txDate.isAfter(LocalDateTime.now().minusMinutes(1));
                        } catch (Exception e) {
                            return false;
                        }
                    }).count();

                    if (recentTxCount >= 3) {
                        user.isFrozen = true;
                        saveData();
                        sendResponse(exchange, 403,
                                "{\"success\": false, \"message\": \"🚨 FRAUD ALERT: Rapid consecutive transactions blocked. Account Frozen!\"}");
                        return;
                    }
                }

                if ("deposit".equalsIgnoreCase(type)) {
                    user.balance += amount;
                    user.history.add(0, new Transaction("Deposit", amount));
                } else if ("withdraw".equalsIgnoreCase(type)) {
                    if (amount <= user.balance) {
                        user.balance -= amount;
                        user.history.add(0, new Transaction("Withdrawal", amount));
                    } else {
                        sendResponse(exchange, 400, "{\"success\": false, \"message\": \"Insufficient funds\"}");
                        return;
                    }
                } else if ("transfer".equalsIgnoreCase(type)) {
                    UserAccount target = database.get(data.get("recipient"));
                    if (target == null || acc.equals(data.get("recipient"))) {
                        sendResponse(exchange, 400, "{\"success\": false, \"message\": \"Invalid recipient\"}");
                        return;
                    }
                    if (target.isFrozen) {
                        sendResponse(exchange, 400,
                                "{\"success\": false, \"message\": \"Recipient account is frozen and cannot receive funds.\"}");
                        return;
                    }
                    if (amount <= user.balance) {
                        user.balance -= amount;
                        target.balance += amount;
                        user.history.add(0, new Transaction("Transfer to " + target.accountId, amount));
                        target.history.add(0, new Transaction("Received from " + acc, amount));
                    } else {
                        sendResponse(exchange, 400, "{\"success\": false, \"message\": \"Insufficient funds\"}");
                        return;
                    }
                } else {
                    sendResponse(exchange, 400, "{\"success\": false, \"message\": \"Invalid type\"}");
                    return;
                }

                saveData();
                sendResponse(exchange, 200, "{\"success\": true}");
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        });

        server.setExecutor(null);
        server.start();
        System.out.println("Server started on http://localhost:8081");
    }

    private static String getRequestBody(HttpExchange exchange) throws IOException {
        InputStreamReader isr = new InputStreamReader(exchange.getRequestBody(), "utf-8");
        BufferedReader reader = new BufferedReader(isr);
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null)
            sb.append(line);
        return sb.toString();
    }

    private static void sendResponse(HttpExchange exchange, int code, String response) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        byte[] bytes = response.getBytes("UTF-8");
        exchange.sendResponseHeaders(code, bytes.length);
        OutputStream os = exchange.getResponseBody();
        os.write(bytes);
        os.close();
    }

    static class StaticFileHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            if (path.equals("/"))
                path = "/index.html";
            File file = new File("public" + path).getCanonicalFile();
            if (!file.getPath().startsWith(new File("public").getCanonicalPath()) || !file.isFile()) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }
            byte[] bytes = Files.readAllBytes(file.toPath());
            String ct = file.getName().endsWith(".html") ? "text/html"
                    : file.getName().endsWith(".css") ? "text/css"
                            : file.getName().endsWith(".js") ? "application/javascript" : "application/octet-stream";
            exchange.getResponseHeaders().add("Content-Type", ct);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        }
    }

    static Map<String, String> parseQuery(String query) {
        Map<String, String> result = new HashMap<>();
        if (query == null)
            return result;
        for (String param : query.split("&")) {
            String[] pair = param.split("=");
            if (pair.length == 2) {
                try {
                    result.put(java.net.URLDecoder.decode(pair[0], "UTF-8"),
                            java.net.URLDecoder.decode(pair[1], "UTF-8"));
                } catch (Exception e) {
                }
            }
        }
        return result;
    }
}
