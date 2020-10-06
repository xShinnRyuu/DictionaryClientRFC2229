import java.lang.System;
import java.io.*;
import java.net.*;
import java.util.*;


//
// This is an implementation of a simplified version of a command
// line dictionary client. The only argument the program takes is
// -d which turns on debugging output.
//


public class CSdict {
  static final int MAX_LEN = 255;
  static Boolean debugOn = false;
  private static Scanner scanner = new Scanner(System.in);
  private static String hostName;
  private static int portNumber;
  private static Socket dictSocket;

  private static final int PERMITTED_ARGUMENT_COUNT = 1;
  public static void main(String [] args) {
    if (args.length == PERMITTED_ARGUMENT_COUNT) {
        debugOn = args[0].equals("-d");
        if (debugOn) {
            System.out.println("Debugging output enabled");
        } else {
            System.err.println("997 Invalid command line option - Only -d is allowed");
            return;
        }
    } else if (args.length > PERMITTED_ARGUMENT_COUNT) {
        System.err.println("996 Too many command line options - Only -d is allowed");
        return;
    }
    while (true) {
      try {
        connectToDictServer();
      } catch (UnknownHostException e) {
        System.err.println("998 Input error while reading commands, terminating.");
        System.exit(-1);
      } catch (SocketTimeoutException e) {
        System.err.println("920 Control connection to " + hostName + " on port " + portNumber + " failed to open.");
      } catch (IOException e) {
        System.err.println("925 Control connection I/O error, closing control connection.");
      } catch (NumberFormatException e) {
        System.err.println("902 Invalid argument.");
      } catch (Exception e) {
        System.err.println("999 Processing error." + e);
      }
    }
  }

  static void connectToDictServer() throws Exception {
    byte[] cmdString = new byte[MAX_LEN];
    System.out.print("csdict> ");
    System.in.read(cmdString);
    String inputString = new String(cmdString, "ASCII");
    String[] inputs = inputString.trim().split("( |\t)+");
    String dictServerCommand = inputs[0].toLowerCase().trim();

    switch(dictServerCommand) {
      case "open":
        if (inputs.length != 3) {
          System.err.println("901 Incorrect number of arguments.");
          break;
        }

        // Create the socket with the given host and port number, includes a 5 second timeout
        hostName = inputs[1];
        portNumber = Integer.parseInt(inputs[2]);
        dictSocket = new Socket();
        dictSocket.connect(new InetSocketAddress(hostName, portNumber), 5000);
        dictSocket.setSoTimeout(5000);

        // This is the output writer (writes to the server)
        OutputStream output = dictSocket.getOutputStream();
        PrintWriter writer = new PrintWriter(output, true);

        // This is the input reader (reads from the server)
        InputStream input = dictSocket.getInputStream();
        BufferedReader reader = new BufferedReader(new InputStreamReader(input));


        // This is to check that the socket connected to the server
        String check220ConnectionCode;
        if ((check220ConnectionCode = reader.readLine()) != null && debugOn) {
          System.out.println("<-- " + check220ConnectionCode);
        }

        // Event cycle funtion to start sending commands to the connected dictionary server
        dictEventCycle(writer, reader);
      break;
      case "quit":
        if (dictSocket != null){
          dictSocket.close();
        }
        if (debugOn){
          System.out.println("> QUIT");
        }
        System.exit(0);
      default:
        if (dictServerCommand.equals("") || dictServerCommand.startsWith("#")){
            break;
        }
        List<String> commandOptions = Arrays.asList("dict", "set", "define", "match", "prefixmatch", "close");
        if (commandOptions.contains(dictServerCommand)){
          System.err.println("903 Supplied command not expected at this time.");
        } else {
          System.err.println("900 Invalid command.");
        }
        break;
    }
  }

  private static void dictEventCycle(PrintWriter writer, BufferedReader reader) throws SocketTimeoutException, IOException{
    String databaseName = "*";

    while (true) {
      System.out.print("csdict> ");
      String openedCommand = scanner.nextLine();
      String[] inputs = openedCommand.trim().split("( |\t)+");
      openedCommand = inputs[0].toLowerCase().trim();

      switch(openedCommand) {
        case "dict":
          if (inputs.length != 1) {
            System.err.println("901 Incorrect number of arguments.");
            break;
          }
          writer.println("SHOW DB");
          if (debugOn) {
            System.out.println("> SHOW DB");
          }
          getBufferedServerReplyDict(reader);
          break;

        case "set":
          if (inputs.length != 2) {
            System.err.println("901 Incorrect number of arguments.");
            break;
          }
          databaseName = inputs[1];
          break;

        case "define":
          if (inputs.length != 2) {
            System.err.println("901 Incorrect number of arguments.");
            break;
          }
          String defineWord = inputs[1];
          writer.println("DEFINE " + databaseName + " " + defineWord);
          if (debugOn) {
            System.out.println("> DEFINE " + databaseName + " " + defineWord);
          }
          getBufferedServerReplyDefine(reader, writer, defineWord, databaseName);
          break;

        case "match":
            if (inputs == null || inputs.length != 2){
                System.err.println("901 Incorrect number of arguments.");
                break;
            }
            String m_cmd = createMatchCommand(databaseName, inputs[1], "exact");
            runMatchCommand(writer, m_cmd);
            fetchMatchResponse(reader, "match");
          break;

        case "prefixmatch":
            if (inputs == null || inputs.length != 2){
                System.err.println("901 Incorrect number of arguments.");
                break;
            }
            String pm_cmd = createMatchCommand(databaseName, inputs[1], "prefix");
            runMatchCommand(writer, pm_cmd);
            fetchMatchResponse(reader, "prefixmatch");

          break;
        case "close":
            writer.println("QUIT");
            if (debugOn) {
              String closeMessage = reader.readLine();
              System.out.println("> QUIT");
              System.out.println("<-- " + closeMessage);
            }
            closeSocket(reader, writer, databaseName);
          return;

        case "quit":
          writer.println("QUIT");
          if (debugOn){
            String quitMessage = reader.readLine();
            System.out.println("> QUIT");
            System.out.println("<-- " + quitMessage);
          }
          System.exit(0);

        default:
          if (openedCommand.equals("") || openedCommand.startsWith("#")){
            break;
          }
          if (openedCommand.equals("open")){
            System.err.println("903 Supplied command not expected at this time.");
          } else {
            System.err.println("900 Invalid command.");
          }
          break;
      }
    }
  }

  private static void getBufferedServerReplyDict(BufferedReader reader) throws IOException {
    String line;
    while(true) {
      line = reader.readLine();
      if (line.startsWith("110")) {
        if (debugOn) {
          line = "<-- " + line;
        } else {
          continue;
        }
      }
      if (line.startsWith("250 ok")) {
        if (debugOn) {
          System.out.println("<-- " + line);
        }
        break;
      }
      System.out.println(line.trim());
    }
  }

  private static void getBufferedServerReplyDefine(BufferedReader reader, PrintWriter writer, String defineWord, String databaseName) throws IOException {
    String line = "";
    while(true) {
      line = reader.readLine();
      if (line.startsWith("150")) {
        if (debugOn) {
          line = "<-- " + line;
        } else {
          continue;
        }
      } if (line.startsWith("151")) {
        String defineOutputs = line.substring(line.indexOf(defineWord) + defineWord.length() + 1);
        if (debugOn) {
          line = "<-- " + line + "\n@" + defineOutputs;
        } else {
          line = "@" + defineOutputs;
        }
      } else if (line.startsWith("250 ok")) {
        if (debugOn) {
          System.out.println("<-- " + line);
        }
        break;
      } else if (line.startsWith("552 no match")) {
        System.out.println("***No definition found***");
        String pm_cmd = createMatchCommand("*", defineWord, "prefix");
        runMatchCommand(writer, pm_cmd);
        fetchMatchResponse(reader, "define");
        break;
      } else if (line.startsWith("550 invalid database,")) {
        System.out.println("***No definition found***");
        System.out.println("****No matches found****");
        break;
      }
      System.out.println(line.trim());
    }
    return;
  }

    private static String createMatchCommand(String dictionary, String word, String strategy){
        return "MATCH " + dictionary + " " + strategy + " " + word;
    }

    private static void runMatchCommand(PrintWriter writer, String cmd) {
        if (debugOn){
            System.out.println("> " + cmd);
        }
        writer.println(cmd);
    }

    private static void fetchMatchResponse(BufferedReader reader, String caller) throws IOException {
      String line;

      while (true){
          line = reader.readLine();
          if (line.startsWith("552 no match")){
            if (debugOn){
              System.out.println("<-- " + line);
            }
            if (caller.equals("define")){
              System.out.println("****No matches found****");
            } else if (caller.equals("match")) {
              System.out.println("*****No matching word(s) found*****");
            } else {
              System.out.println("****No matching word(s) found****");
            }
            break;
          } else if (line.startsWith("152") || line.startsWith("250 ok")){
            if (debugOn){
              System.out.println("<-- " + line);
            }
            if (line.startsWith("250 ok")){
              break;
            }
          } else {
            System.out.println(line);
          }
      }
    }

    private static void closeSocket(BufferedReader reader, PrintWriter writer, String databaseName) throws IOException {
      if (socketIsRunning()){
          dictSocket.close();
          dictSocket = null;
          reader = null;
          writer = null;
          databaseName = "*";
      }
    }

    private static boolean socketIsRunning(){
      return (dictSocket != null) && (!dictSocket.isClosed());
    }

}
