package org.jrd.agent;

import org.jrd.backend.data.MetadataProperties;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * This class handles the socket accepting and request processing from the
 * decompiler
 *
 * @author pmikova
 */
public class AgentActionWorker extends Thread {

    private InstrumentationProvider provider;
    private Boolean abort = false;

    private static final String AGENT_ERROR_ID = "ERROR";

    private static String toError(String message) {
        return AGENT_ERROR_ID + " " + message;
    }

    private static String toError(Throwable ex) {
        return toError(ex.toString());
    }

    public AgentActionWorker(Socket socket, InstrumentationProvider provider) {
        this.provider = provider;

        try {
            executeRequest(socket, provider);
        } catch (Exception e) {
            AgentLogger.getLogger().log(new RuntimeException("Error when trying to execute the request. Cause: ", e));
            try {
                socket.close();
            } catch (IOException e1) {
                AgentLogger.getLogger().log(new RuntimeException("Error when trying to close the socket. Cause: ", e1));
            }
        }
    }

    private void executeRequest(Socket socket, InstrumentationProvider localProvider) {
        InputStream is = null;
        try {
            is = socket.getInputStream();
        } catch (IOException e) {
            AgentLogger.getLogger().log(new RuntimeException("Error when opening the socket input stream. Cause: ", e));
            try {
                socket.close();
            } catch (IOException e1) {
                AgentLogger.getLogger().log(new RuntimeException("Error when closing the socket. Cause: ", e1));
            }
            return;
        }

        OutputStream os = null;
        try {
            os = socket.getOutputStream();
        } catch (IOException e) {
            AgentLogger.getLogger().log(new RuntimeException("Error when opening the socket output stream. Cause: ", e));
            try {
                socket.close();
            } catch (IOException e1) {
                AgentLogger.getLogger().log(new RuntimeException("Error when closing the socket. Cause: ", e1));
            }
            return;
        }
        BufferedReader inputStream = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        BufferedWriter outputStream = new BufferedWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8));

        String line = null;
        try {
            line = inputStream.readLine();
        } catch (IOException e) {
            AgentLogger.getLogger().log(new RuntimeException("Exception occurred during reading of the line: ", e));
        }
        try {
            if (null == line) {
                outputStream.write(toError("Agent received no command.") + "\n");
                outputStream.flush();
            } else {
                writeToStreamBasedOnLine(socket, localProvider, inputStream, outputStream, line);
            }
        } catch (IOException e) {
            AgentLogger.getLogger().log(new RuntimeException("Error when trying to process the request:", e));
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                AgentLogger.getLogger().log(new RuntimeException("Error when trying to close the socket:", e));
            }
        }
    }

    private void writeToStreamBasedOnLine(
            Socket socket, InstrumentationProvider localProvider, BufferedReader inputStream, BufferedWriter outputStream, String line
    ) throws IOException {
        switch (line) {
            case "HALT":
                AgentLogger.getLogger().log("Agent received HALT command, closing socket.");
                closeSocket(outputStream, socket);
                AgentLogger.getLogger().log("Agent received HALT command, removing instrumentation");
                localProvider.detach();
                break;
            case "SEARCH_CLASSES":
                getAllFilteredClasses(inputStream, outputStream);
                break;
            case "CLASSES":
                getAllLoadedClasses(outputStream, false);
                break;
            case "CLASSES_WITH_INFO":
                getAllLoadedClasses(outputStream, true);
                break;
            case "OVERRIDES":
                getAllOverridesClasses(outputStream);
                break;
            case "BYTES":
                sendByteCode(inputStream, outputStream);
                break;
            case "VERSION":
                getVersion(outputStream);
                break;
            case "OVERWRITE":
                receiveByteCode(inputStream, outputStream, ReceivedType.OVERWRITE_CLASS);
                break;
            case "ADD_CLASS":
                receiveByteCode(inputStream, outputStream, ReceivedType.ADD_CLASS);
                break;
            case "ADD_JAR":
                receiveByteCode(inputStream, outputStream, ReceivedType.ADD_JAR);
                break;
            case "INIT_CLASS":
                initClass(inputStream, outputStream);
                break;
            case "REMOVE_OVERRIDES":
                removeOverrides(inputStream, outputStream);
                break;
            case "HELLO":
                outputStream.write("Agent HELLO handshake: '" + line + "'." + "\n");
                outputStream.flush();
                break;
            default:
                outputStream.write(toError("Agent received unknown command: '" + line + "'.") + "\n");
                outputStream.flush();
                break;
        }
    }

    private interface ListInjector<T> {
        void inject(BlockingQueue<T> target) throws InterruptedException;
    }

    private void getList(BufferedWriter out, String id, ListInjector<String> injector) throws IOException {
        out.write(id);
        out.newLine();
        BlockingQueue<String> classNames = new LinkedBlockingQueue<String>(1024);
        new Thread(() -> {
            try {
                injector.inject(classNames);
            } catch (InterruptedException e) {
                AgentLogger.getLogger().log(e);
            }
        }).start();
        while (true) {
            String x = classNames.poll();
            if (x == null) {
                continue;
            }
            if ("---END---".equals(x)) {
                break;
            } else {
                out.write(x);
                out.newLine();
            }
        }
        out.flush();
    }

    private void getAllLoadedClasses(BufferedWriter out, boolean doGetInfo) throws IOException {
        getList(out, "CLASSES", new ListInjector<String>() {
            @Override
            public void inject(BlockingQueue<String> target) throws InterruptedException {
                provider.getClasses(target, abort, doGetInfo, Optional.empty());
            }
        });
    }

    private void getAllFilteredClasses(BufferedReader in, BufferedWriter out) throws IOException {
        final String substringAndRegexLineAndDetails = in.readLine();
        boolean doGetInfo = (substringAndRegexLineAndDetails != null) ? substringAndRegexLineAndDetails.endsWith(" true") : false;
        final Optional<ClassFilter> filter = ClassFilter.create(substringAndRegexLineAndDetails);
        getList(out, "SEARCH_CLASSES", new ListInjector<String>() {
            @Override
            public void inject(BlockingQueue<String> target) throws InterruptedException {
                provider.getClasses(target, abort, doGetInfo, filter);
            }
        });
    }

    private void getAllOverridesClasses(BufferedWriter out) throws IOException {
        getList(out, "OVERRIDES", new ListInjector<String>() {
            @Override
            public void inject(BlockingQueue<String> target) throws InterruptedException {
                provider.getOverrides(target);
            }
        });
    }

    private void sendByteCode(BufferedReader in, BufferedWriter out) throws IOException {
        String className = in.readLine();
        if (className == null) {
            out.write(toError("No class name provided for the get bytes command.") + "\n");
            out.flush();
            return;
        }
        try {
            byte[] body = provider.findClassBody(className);
            String encoded = Base64.getEncoder().encodeToString(body);
            out.write("BYTES");
            out.newLine();
            out.write(encoded);
            out.newLine();
        } catch (Throwable ex) {
            AgentLogger.getLogger().log(ex);
            out.write(toError(ex) + "\n");
        }
        out.flush();
    }

    private void getVersion(BufferedWriter out) throws IOException {
        try {
            out.write("VERSION");
            out.newLine();
            out.write(MetadataProperties.getInstance().toString());
            out.newLine();
        } catch (Throwable ex) {
            AgentLogger.getLogger().log(ex);
            out.write(toError(ex) + "\n");
        }
        out.flush();
    }

    private interface ParametrisedRunner {
        void run(String args) throws Exception;
    }

    private void executeParametrisedNoReturnCommand(
            BufferedReader in, BufferedWriter out, String help, ParametrisedRunner parametrisedRunner
    ) throws IOException {
        String args = in.readLine();
        if (args == null) {
            out.write(toError(help) + "\n");
            out.flush();
            return;
        }
        try {
            parametrisedRunner.run(args);
            out.write("DONE");
            out.newLine();
        } catch (Throwable ex) {
            AgentLogger.getLogger().log(ex);
            out.write(toError(ex) + "\n");
        }
        out.flush();
    }

    private void initClass(BufferedReader in, BufferedWriter out) throws IOException {
        executeParametrisedNoReturnCommand(in, out, "No FQN provided for the init class command.", new ParametrisedRunner() {
            @Override
            public void run(String arg) throws Exception {
                Class.forName(arg);
            }
        });
    }

    private void removeOverrides(BufferedReader in, BufferedWriter out) throws IOException {
        executeParametrisedNoReturnCommand(in, out, "No regex provided for the remove override. Try .*", new ParametrisedRunner() {
            @Override
            public void run(String pattern) throws Exception {
                int removed = provider.cleanOverrides(pattern);
                if (removed == 0) {
                    throw new Exception("Nothing removed by " + pattern + ".Try OVERRIDES to see active overrides");
                }
            }
        });
    }

    private void receiveByteCode(BufferedReader in, BufferedWriter out, ReceivedType rewroteAddJar) throws IOException {
        executeParametrisedNoReturnCommand(in, out, "No class name provided for the overwrite command.", new ParametrisedRunner() {
            @Override
            public void run(String className) throws Exception {
                String classBodyBase64 = in.readLine();
                if (classBodyBase64 == null) {
                    out.write(toError("No class body provided for the overwrite command.") + "\n");
                    out.flush();
                    return;
                }
                switch (rewroteAddJar) {
                    case OVERWRITE_CLASS:
                        provider.setClassBody(className, Base64.getDecoder().decode(classBodyBase64));
                        break;
                    case ADD_CLASS:
                        provider.addClass(className, Base64.getDecoder().decode(classBodyBase64));
                        //initClass(in, out); returns
                        break;
                    case ADD_JAR:
                        provider.addJar(className, Base64.getDecoder().decode(classBodyBase64));
                        break;
                    default:
                        throw new RuntimeException("Unknown action to receiveByteCode: " + rewroteAddJar);
                }
            }
        });
    }

    private void closeSocket(BufferedWriter out, Socket socket) throws IOException {
        out.write("GOODBYE");
        out.flush();
        socket.close();
        ConnectionDelegator.gracefulShutdown();
        AgentLogger.getLogger().log("done");
    }
}
