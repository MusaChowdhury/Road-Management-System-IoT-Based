import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Scanner;
import java.util.Vector;

enum Request {
    Navigation,
    None
}

public class Main {

    public static void main(String[] args) throws Exception {
        ConfigurationChecker.checkAndStart();


    }
}


class ConfigurationChecker {

    private static Scanner in = new Scanner(System.in);


    static void checkAndStart() {
        System.out.println("Broadcast Address Is The Last Address of Given Network.");
        System.out.println("Incorrect Broadcast Address Will Break Every Functionality of The System.");
        System.out.print("Current Broadcast Address of This Computer (Example 192.168.1.255): " );
        classApi.broadcastIP = in.nextLine();
        System.out.println("Access Local Server At : http://localhost:"+ classApi.httpPort + "/");
        System.out.println("Press ENTER KEY to Start Server.......");
        in.nextLine();
        try {
            Engine.start();
        } catch (Exception e) {
            System.out.println("Failed To Start Core Of The SERVER");
        }


    }

}


class classApi {
    static  final int httpPort = 20000;
    static final int arduinoPort = 4000;
    static final int serverPort = 3000;
    static String broadcastIP = null;
    static volatile DatagramSocket udp;
    static volatile Vector<DataContainer> rQueue = new Vector<>();

    static {
        try {
            udp = new DatagramSocket(serverPort);
        } catch (SocketException e) {
            e.printStackTrace();
        }
    }

    static synchronized void addRequest(DataContainer req) {
        if (!rQueue.contains(req)) {
            rQueue.add(req);
        }
    }

    static synchronized void sendBroadcast(String data) throws IOException {
        data = data + "\0\n";
        udp.send(new DatagramPacket(data.getBytes(StandardCharsets.US_ASCII), data.length() - 1, InetAddress.getByName(broadcastIP), arduinoPort));
    }

    static synchronized void sendToIP(String data, InetAddress ip) throws IOException {
        data = data + "\0\n";
        udp.send(new DatagramPacket(data.getBytes(StandardCharsets.US_ASCII), data.length() - 1, ip, arduinoPort));
    }

}

class Engine {

    static Thread viewer = new Thread(new Runnable() {
        @Override
        public void run() {
            try {
                while (true) {
                    System.out.println("\nNavigation Request in Queue : " + classApi.rQueue);
                    System.out.println("Emergency Request Arrived : " + EmergencyReqHandler.requested);
                    System.out.println("Emergency Request Pending/Waiting : " + EmergencyReqHandler.processing);
                    System.out.println("Emergency Request Completed : " + EmergencyReqHandler.completed);
                    System.out.println("Emergency Request Log/Executed : " + EmergencyReqHandler.delivered + " \n");
                    //Refresh delay
                    Thread.sleep(1000);
                }
            } catch (Exception e) {
                System.out.println("Caught Exception in Viewer Thread");
                e.printStackTrace();
            }
        }
    });
    static Thread receiver = new Thread(new Runnable() {
        @Override
        public void run() {
            try {
                //DatagramSocket udp = new DatagramSocket(3000);
                while (true) {
                    DatagramPacket upComing = new DatagramPacket(new byte[1000], 1000);
                    classApi.udp.receive(upComing);
                    String data = new String(upComing.getData(), 0, upComing.getLength());
                    System.out.println("<<- Incoming Data :" + data.replace("\n", "").trim());
                    if (data.contains("?")) {

                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    byte reply[] = "@ok#\0".getBytes(StandardCharsets.US_ASCII);
                                    //Thread.sleep(1000);
                                    classApi.udp.send(new DatagramPacket(reply, reply.length, upComing.getAddress(), upComing.getPort()));
                                } catch (Exception e) {
                                    System.out.println("Caught Exception In Acknowledgement Thread");
                                }
                            }
                        }).start();

                    } else if (data.contains("EW")) {
                        StringBuilder reply = new StringBuilder();
                        for (EmergencyReqHandler.ReqData i : EmergencyReqHandler.processing) {
                            if (i.state == EmergencyReqHandler.State.Waiting) {
                                reply.append(i.id + ",");
                            }
                        }
                        if (reply.charAt(reply.length() - 1) == ',') {
                            reply.deleteCharAt(reply.length() - 1);
                        }
                        classApi.udp.send(new DatagramPacket(reply.toString().getBytes(StandardCharsets.UTF_8), reply.toString().length(), upComing.getAddress(), upComing.getPort()));
                        //classApi.udp.send(new DatagramPacket(reply.toString().getBytes(StandardCharsets.UTF_8), reply.toString().length(), upComing.getAddress(), 4000));

                    } else if (data.contains("n")) {
                        DataContainer temp = new DataContainer(data);
                        if ((temp.id != -1) && (temp.data != Request.None)) {
                            classApi.addRequest(temp);
                        }
                    } else {
                        EmergencyReqHandler.emergencyRequestHandler(data, upComing.getAddress());
                    }
                }

            } catch (Exception e) {
                System.out.println("Caught Exception in Receiver Thread");
                e.printStackTrace();
            }
        }
    });
    static Thread sender = new Thread(new Runnable() {
        @Override
        public void run() {
            try {
                while (true) {
                    if (!classApi.rQueue.isEmpty()) {
                        DataContainer current = classApi.rQueue.remove(0);
                        if (current.data == Request.Navigation) {
                            int a = current.id;
                            int b = current.id;
                            ArrayList<DataContainer> processed = new ArrayList<>();
                            for (DataContainer i : classApi.rQueue) {
                                if (i.data == Request.Navigation) {
                                    processed.add(i);
                                    if (i.id <= a) a = i.id;
                                    if (i.id >= b) b = i.id;
                                }
                            }

                            classApi.rQueue.removeAll(processed);
                            if (b - a > 0) {
                                StringBuilder navigation = new StringBuilder("@n");
                                for (int i = a; i <= b; ++i) {
                                    navigation.append(i);
                                }
                                navigation.append("#");
                                classApi.sendBroadcast(navigation.toString());

                            }

                        }
                    }

                    // Processing delay
                    Thread.sleep(3000);
                }
            } catch (Exception e) {
                System.out.println("Caught Exception In Processing Thread");
                e.printStackTrace();
            }
        }
    });

    static void start() throws Exception {
        EmergencyReqHandler.start();
        Engine.receiver.start();
        Engine.viewer.start();
        Engine.sender.start();
        DataHandler.start();

    }

}

class DataContainer {
    int id = -1;
    Request data = Request.None;

    DataContainer(String s) {
        try {
            s = s.trim();
            s = s.replace("\r", "");
            s = s.replace("\n", "");
            String[] split = s.split("-");
            this.id = Integer.parseInt(split[0]);
            //System.out.println(String.format(" id = %d data = %s and comparing value with r = %d & n = %d",id, split[1],split[1].compareTo("r"),split[1].compareTo("n")));
            if (split[1].compareTo("n") == 0) {
                data = Request.Navigation;
            } else {
                data = Request.None;
            }

        } catch (Exception e) {
            System.out.println("Invalid or Corrupted Data Type");
            this.id = -1;
            this.data = Request.None;
        }

    }

    @Override
    public int hashCode() {
        return (this.id * 7) + (this.data.ordinal() * 3);
    }

    @Override
    public boolean equals(Object d) {
        if (d instanceof DataContainer) {
            DataContainer tmp = (DataContainer) d;
            return (tmp.id == this.id) && (tmp.data == this.data);
        }
        return false;
    }

    @Override
    public String toString() {
        return String.format("{ID : %d, Type = %s}", this.id, this.data.toString());
    }

}


class EmergencyReqHandler {
    static volatile Vector<ReqData> requested = new Vector();
    static volatile Vector<ReqData> processing = new Vector();
    static volatile Vector<ReqData> completed = new Vector();
    static volatile Vector<ReqData> delivered = new Vector();
    private static Thread mainHandler = new Thread(new Runnable() {
        @Override
        public void run() {
            try {

                while (true) {
                    if (processing.isEmpty()) {
                        for (ReqData i : requested) {
                            System.out.println("->> Send data to esp for acknowledgement as req received " + i.id);
                            classApi.sendToIP("@R#", i.ip);
                            Thread.sleep(500);
                        }
                    } else {
                        Vector<ReqData> alreadyReceived = new Vector<>();
                        Vector<ReqData> pending = new Vector<>();

                        for (ReqData i : requested) {
                            if (processing.contains(new ReqData(i.id, i.ip, State.Received))) {
                                alreadyReceived.add(i);
                                alreadyReceived.add(new ReqData(i.id, i.ip, State.Received));
                                pending.add(new ReqData(i.id, i.ip, State.Waiting));
                            } else {
                                System.out.println("->> Send data to esp for acknowledgement as req received " + i.id);
                                classApi.sendToIP("@R#", i.ip);
                                Thread.sleep(500);

                            }
                        }
                        requested.removeAll(alreadyReceived);
                        processing.removeAll(alreadyReceived);
                        processing.addAll(pending);
                    }
                    if (!processing.isEmpty()) {
                        Vector<ReqData> processed = new Vector<>();
                        Vector<ReqData> ipShift = new Vector<>();
                        for (ReqData i : completed) {
                            if (processing.contains(new ReqData(i.id, i.ip, State.Waiting))) {
                                processed.add(new ReqData(i.id, i.ip, State.Waiting));
                                try {
                                    completed.get(completed.indexOf(new ReqData(i.id, i.ip, State.Completed))).ip = processing.get(processing.indexOf(new ReqData(i.id, i.ip, State.Waiting))).ip;
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }

                            }
                        }
                        processing.removeAll(processed);


                    }

                    if (delivered.isEmpty()) {
                        for (ReqData i : completed) {
                            System.out.println("->> Send data to esp for acknowledgement as req completed " + i.id);
                            classApi.sendToIP("@C#", i.ip);
                            Thread.sleep(500);

                        }
                    } else {
                        Vector<ReqData> filter = new Vector<>();
                        Vector<ReqData> processed = new Vector<>();
                        for (ReqData i : completed) {
                            if (delivered.contains(new ReqData(i.id, i.ip, State.Delivered))) {
                                processed.add(new ReqData(i.id, i.ip, State.Completed));
                                filter.add(new ReqData(i.id, i.ip, State.Delivered));
                            } else {
                                System.out.println("->> Send data to esp for acknowledgement as req completed " + i.id);
                                classApi.sendToIP("@C#", i.ip);
                                Thread.sleep(500);

                            }
                        }
                        completed.removeAll(processed);
                        delivered.removeAll(filter);


                    }

                    // process handling delay
                    Thread.sleep(500);

                }
            } catch (Exception e) {
                System.out.println("Exception Caught In Emergency Request Handler -> Main Handler");
            }

        }
    });

    public static synchronized void emergencyRequestHandler(String s, InetAddress ip) {
        int id = -1;
        State type = null;
        try {
            s = s.trim();
            s = s.replace("\r", "");
            s = s.replace("\n", "");
            String[] split = s.split("-");
            id = Integer.parseInt(split[0]);
            String data = split[1];

            if (data.compareTo("r") == 0) {
                type = State.Requested;
            } else if (data.compareTo("R") == 0) { // stands for request received ensured
                type = State.Received;
            } else if (data.compareTo("C") == 0) { // stands for request is completed by control Room
                type = State.Completed;
            } else if (data.compareTo("D") == 0) {  // stands for request completed ensured and delivered
                type = State.Delivered;
            }

            if (id == -1 || type == null) {
                System.out.println("Invalid or Corrupted Data Type");
                return;
            }
            ReqData temp = new ReqData(id, ip, type);
            if (type == State.Requested && !requested.contains(temp)) {
                requested.add(temp);
            } else if (type == State.Completed && !completed.contains(temp) && processing.contains(new ReqData(id, ip, State.Waiting))) {
                completed.add(temp);
            } else if (type == State.Received && !processing.contains(temp) && requested.contains(new ReqData(id, ip, State.Requested))) {
                processing.add(temp);
            } else if (type == State.Delivered && !delivered.contains(temp) && completed.contains(new ReqData(id, ip, State.Completed))) {
                delivered.add(temp);
            }


        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Exception Caught In Emergency Request Handler -> Initiator");
        }

    }

    public static void start() {
        if (!mainHandler.isAlive()) {
            mainHandler.start();
        }
    }

    static enum State {
        Requested,
        Received,
        Waiting,
        Completed,
        Delivered
    }

    static class ReqData {
        int id;
        State state;
        InetAddress ip;


        public ReqData(int id, InetAddress ip, State state) {
            this.id = id;
            this.state = state;
            this.ip = ip;
        }


        @Override
        public String toString() {
            return "[ Emergency Request Info {" +
                    "id=" + id +
                    ", state=" + state +
                    ", ip=" + ip +
                    "} ]";
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ReqData reqData = (ReqData) o;
            return id == reqData.id && state == reqData.state;
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, state, ip);
        }
    }


}

class DataHandler implements HttpHandler {

    public static void start() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(classApi.httpPort), 0);
        server.createContext("/", new FontPage());
        server.createContext("/update", new DataHandler());
        server.setExecutor(null); // creates a default executor
        server.start();
    }

    private static String formatter(int n) {
        return String.format("%d;%s;%s", n, "Not Defined", "Police/Crime");
    }

    private static String dataGenerator(int n[]) {
        if (n == null || n.length == 0) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < n.length; ++i) {
            builder.append(formatter(n[i]) + ",");
        }
        if (builder.length() > 0) {
            builder.deleteCharAt(builder.length() - 1);
        } else return null;

        return builder.toString();
    }


    @Override
    public void handle(HttpExchange t) throws IOException {


        if (t.getRequestMethod().equalsIgnoreCase("POST")) {

            t.sendResponseHeaders(200, 0);
            int contentLength = Integer.parseInt(t.getRequestHeaders().getFirst("Content-length"));
            byte data[] = new byte[contentLength];
            t.getRequestBody().read(data);
            String received = new String(data);
            System.out.println("<-Send Data From Emergency Interface Background Process, Data RECEIVED :" + received);
            EmergencyReqHandler.emergencyRequestHandler(received, InetAddress.getLocalHost());

        } else {

            t.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            Vector<Integer> ids = new Vector<>();
            for (EmergencyReqHandler.ReqData i : EmergencyReqHandler.processing) {
                if (i.state == EmergencyReqHandler.State.Waiting) {
                    ids.add(i.id);
                }
            }

            //int ids[] = {1, 2, 4};
            int hold[] = new int[ids.size()];
            for (int i = 0; i < hold.length; ++i) {
                hold[i] = ids.get(i);
            }
            if (hold.length > 0) {
                String response = dataGenerator(hold);
                t.sendResponseHeaders(200, response.length());
                OutputStream os = t.getResponseBody();
                os.write(response.getBytes());
                os.close();
                System.out.println("->Requested Data Through Emergency Interface Background Process, Data SEND : " + response);
            } else {
                String response = "n";
                t.sendResponseHeaders(200, response.length());
                OutputStream os = t.getResponseBody();
                os.write(response.getBytes());
                os.close();
                System.out.println("->Requested Data Through Emergency Interface Background Process, Data SEND : " + response);
            }


        }

    }
}

class FontPage implements HttpHandler {

    @Override
    public void handle(HttpExchange t) throws IOException {
        File path = new File("./index.html");
        System.out.println("->Requested Font page");
        t.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        t.getResponseHeaders().add("Content-Type", "text/html");
        t.sendResponseHeaders(200, path.length());
        OutputStream os = t.getResponseBody();
        os.write(Files.readAllBytes(path.toPath()));
        os.close();
    }


}
