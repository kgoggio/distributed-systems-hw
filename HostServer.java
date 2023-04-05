//Kyle Goggio
//3/5/23
//jdk 19.0.1

/*TO EXECUTE: 

1. Start the HostServer in some shell. >> java HostServer

1. start a web browser and point it to http://localhost:4242. Enter some text and press
the submit button to simulate a state-maintained conversation.

2. start a second web browser, also pointed to http://localhost:4242 and do the same. Note
that the two agents do not interfere with one another.

3. To suggest to an agent that it migrate, enter the string "migrate"
in the text box and submit. The agent will migrate to a new port, but keep its old state.

During migration, stop at each step and view the source of the web page to see how the
server informs the client where it will be going in this stateless environment.


/* 2012-05-20 Version 2.0

Thanks John Reagan for updates to original code by Clark Elliott.

Modified further on 2020-05-19

-----------------------------------------------------------------------

Play with this code. Add your own comments to it before you turn it in.

-----------------------------------------------------------------------

NOTE: This is NOT a suggested implementation for your agent platform,
but rather a running example of something that might serve some of
your needs, or provide a way to start thinking about what YOU would like to do.
You may freely use this code as long as you improve it and write your own comments.

-----------------------------------------------------------------------



COMMENTS:

This is a simple framework for hosting agents that can migrate from
one server and port, to another server and port. For the example, the
server is always localhost, but the code would work the same on
different, and multiple, hosts.

State is implemented simply as an integer that is incremented. This represents the state
of some arbitrary conversation.

The example uses a standard, default, HostListener port of 4242.

-----------------------------------------------------------------------------------

DESIGN OVERVIEW

Here is the high-level design, more or less:

HOST SERVER
  Runs on some machine
  Port counter is just a global integer incrememented after each assignment
  Loop:
    Accept connection with a request for hosting
    Spawn an Agent Looper/Listener with the new, unique, port

AGENT LOOPER/LISTENER
  Make an initial state, or accept an existing state if this is a migration
  Get an available port from this host server
  Set the port number back to the client which now knows IP address and port of its
         new home.
  Loop:
    Accept connections from web client(s)
    Spawn an agent worker, and pass it the state and the parent socket blocked in this loop
  
AGENT WORKER
  If normal interaction, just update the state, and pretend to play the animal game
  (Migration should be decided autonomously by the agent, but we instigate it here with client)
  If Migration:
    Select a new host
    Send server a request for hosting, along with its state
    Get back a new port where it is now already living in its next incarnation
    Send HTML FORM to web client pointing to the new host/port.
    Wake up and kill the Parent AgentLooper/Listener by closing the socket
    Die

WEB CLIENT
  Just a standard web browser pointing to http://localhost:4242 to start.

  -------------------------------------------------------------------------------*/


import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;


 

//agent worker objects are made by the agentlisteners;
//they process requests made at the active ports where the agentlistner objects are stored
//they accept requests, and search for the word migrate in the request string (in the form of a get request from an html form
//if "migrate" is found in the request string, the worker finds the next open port, and makes the client jump to that port
class AgentWorker extends Thread {
	
  Socket sock; //connects with the client
  agentHolder parentAgentHolder; //object that holds state info that can be passed between ports
  int localPort; //port used for this request
  
  //constructor; creates an agent worker thread with the socket, port number, and agent holder objects above passed in...
 //will let agent worker have access to the local port and parent agent holder info
  AgentWorker (Socket s, int prt, agentHolder ah) {
    sock = s;
    localPort = prt;
    parentAgentHolder = ah;
  }
  public void run() {
    
    PrintStream out = null; //initializes print stream that will send info through the socket to client
    BufferedReader in = null; //initializes the buffered reader, which read whatever is sent to server by client
    
    String NewHost = "localhost"; //host name that the server will use
    //port the main worker will run on
    int NewHostMainPort = 4242; //main port used by the server; 
    String buf = "";//initialized string that will  be used to check and read port number in for loop later
    int newPort; //initialized new port to whcih all will be migrated to when the migration occurs
    Socket clientSock; //
    BufferedReader fromHostServer;//will read the stuff written to the client by the host
    PrintStream toHostServer; //client will use this to print stuff to the host server
    
    try {
      out = new PrintStream(sock.getOutputStream()); //assigns the printstream to the socket's output stream
      in = new BufferedReader(new InputStreamReader(sock.getInputStream())); //assigns the buffered reader to the socket's inputstream
      
      
      String inLine = in.readLine();//reads the first line of the input stream; will read the get requests (or atleast i'm pretty sure that's what its for)

      StringBuilder htmlString = new StringBuilder();
      //this will be used to build dynamic html
      System.out.println(); 
      System.out.println("Request line: " + inLine); //logs client requests
      
      if(inLine.indexOf("migrate") > -1) {
	//checks to see if migrate is contained in the request line
	
	//creates new socket separate from the one at 4242
	clientSock = new Socket(NewHost, NewHostMainPort); //separate socket from the main one at 4242
	fromHostServer = new BufferedReader(new InputStreamReader(clientSock.getInputStream())); //new one at the next host/port (presumably for when migrate shows up in text)
	
	toHostServer = new PrintStream(clientSock.getOutputStream());//creates the new socket that the agent worker migrates to, along with the new port
	toHostServer.println("Please host me. Send my port! [State=" + parentAgentHolder.agentState + "]");
	toHostServer.flush();//clears the memory
	
	//this is an infinite for loop that checks for valid port numbers
	for(;;) {
	  //read the line and check it for what looks to be a valid port
	  buf = fromHostServer.readLine(); //line is read, and checked for a port= in the get request
	  if(buf.indexOf("[Port=") > -1) { 
	    break;//loop breaks if it happens. why not just use a while loop? possibly has to do with faster speed--while loops are more runtime demanding if i recall right
	  }
	}
	
	//takes a string from the substring which starts at 6 + the index of the "port=" string, and ends at the index of the closed bracket from the port number
	String tempbuf = buf.substring( buf.indexOf("[Port=")+6, buf.indexOf("]", buf.indexOf("[Port=")) );
	//the new port number is parsed from the requested new port number
	newPort = Integer.parseInt(tempbuf);
	
	System.out.println("newPort is: " + newPort);//prints message on console
	//below is the start of the dynamic html that will be built in response to client requests (including migration request)
	//creates the dynamic html telling the client the new port that the client will be migrated to
	htmlString.append(AgentListener.sendHTMLheader(newPort, NewHost, inLine));
	//tells user on server page that the miration is occuring
	htmlString.append("<h3>We are migrating to host " + newPort + "</h3> \n");
	htmlString.append("<h3>View the source of this page to see how the client is informed of the new location.</h3> \n");

	htmlString.append(AgentListener.sendHTMLsubmit());//line finishes the html by finishing all the headers, and restoring it so that you can submit new text...
	//...and press submit button; like what we had to do with miniwebserver
	
	//logs that the parent listening loop is being shut down
	System.out.println("Killing parent listening loop.");
	ServerSocket ss = parentAgentHolder.sock; 	//this grabs the same socket used up to migration, and migrates it to the new port

	//old socket is now closed, since its stuff was migrated to the new one; no longer need it; failing to close it will pose potential security risk
	ss.close();
	
	
      } else if(inLine.indexOf("person") > -1) {
	//checks to see if the word person was entered into the web page by client
	parentAgentHolder.agentState++; //if so then the state data is accumulated up by one
	//send the html back to the user displaying the agent state and form
	htmlString.append(AgentListener.sendHTMLheader(localPort, NewHost, inLine)); //builds the html header, adds it to the response string
	htmlString.append("<h3>We are having a conversation with state   " + parentAgentHolder.agentState + "</h3>\n"); //adds a message letting client know attempt was successful
	htmlString.append(AgentListener.sendHTMLsubmit()); //adds the submit button, which will "reset" the page	
	
      } else {
	//this means the request was invalid, builds a version of dynamic html page informing the user  of this
	htmlString.append(AgentListener.sendHTMLheader(localPort, NewHost, inLine)); //this builds the html header and adds it to the html string that's going to be sent to the client
	htmlString.append("You have not entered a valid request!\n"); //tells client rquest was invalid
	htmlString.append(AgentListener.sendHTMLsubmit());	//adds the submit button, which will "reset" the page	
	
	
      }
      //sends the dynamically assembled html to the client
      AgentListener.sendHTMLtoStream(htmlString.toString(), out);
      
      //socket closes, the connection is broken
      sock.close();
      
      
    } catch (IOException ioe) {
      System.out.println(ioe);
    }
  }
  
}


//agent holder holds state info/resources so we can track the agent state and pass that info between ports
class agentHolder {
  
  ServerSocket sock;//activer serversocket object
  //basic state info; if i wanted to resue this code, the agent state could be anything i'd want it to be,
  //...including custom-made objects
  int agentState;
  
  //basic constructor
  agentHolder(ServerSocket s) { sock = s;}
}

//agent listener objects track individual ports and respond to requests made on them (here from a standard web browser;
//agentlistener objects are created by the host server when the main port at 4242 gets a new request
class AgentListener extends Thread {
  //instance vars
  Socket sock;
  int localPort;
  
  //passes in socket and port number so they can be stored locally
  AgentListener(Socket As, int prt) {
    sock = As;
    localPort = prt;
  }
  //defualt stte must be 0 because conversation hasn't started yet
  int agentState = 0;
  

  public void run() {
    BufferedReader in = null;
    PrintStream out = null;
    String NewHost = "localhost";
    System.out.println("In AgentListener Thread");		
    try {
      String buf;
      out = new PrintStream(sock.getOutputStream()); //writes string out to the client
      in =  new BufferedReader(new InputStreamReader(sock.getInputStream()));//reads inputed string from client
      

      buf = in.readLine(); //reads first line of the buffered reader
      

      if(buf != null && buf.indexOf("[State=") > -1) { //if there is a state, we parse it and store it in the agent state variable
	
	String tempbuf = buf.substring(buf.indexOf("[State=")+7, buf.indexOf("]", buf.indexOf("[State="))); //extracts the string containing state info using sub string method
	
	agentState = Integer.parseInt(tempbuf); //parses the atent state
	System.out.println("agentState is: " + agentState); //logs this to console; useful when trying to audit your server logs
	
      }
      
      System.out.println(buf);
      //string builder to hold the html response
      StringBuilder htmlResponse = new StringBuilder();//will conatin the concatenated html response string
      
      
      htmlResponse.append(sendHTMLheader(localPort, NewHost, buf)); //sends the start of the page, including the state info and current port (state should be 0 at start), and displays the basic html web form
      htmlResponse.append("Now in Agent Looper starting Agent Listening Loop\n<br />\n"); //adds in this html string informing client that the agent listening looper has started
      htmlResponse.append("[Port="+localPort+"]<br/>\n");
      htmlResponse.append(sendHTMLsubmit()); //finishes the dynamic html response page

      sendHTMLtoStream(htmlResponse.toString(), out); //this sends the html response to the print stram 
      

      ServerSocket servsock = new ServerSocket(localPort,2);//creates a new agent listener doorbell socket that will upon making a connection, spawn agent worker threads
      
      agentHolder agenthold = new agentHolder(servsock); //creates local agenthold object that will store the socket and agent state information
      agenthold.agentState = agentState; //updates the atenthold's agent state attribute to the one stored in the run method
      
      //doorbell socket is waiting for doorbell to ring
      while(true) {
	sock = servsock.accept();//creates a connection witht he incoming client
	System.out.println("Got a connection to agent at port " + localPort);
	//prints out message containing info on receieved connections
	new AgentWorker(sock, localPort, agenthold).start(); //agent worker thread is spawned
      }
      
    } catch(IOException ioe) {
      //catches IO exception errors for debugging
      System.out.println("Either connection failed, or just killed listener loop for agent at port " + localPort);
      System.out.println(ioe);
    }
  }
  //this creates the html header; this helps start the reset of the page; if you don't send this...
  //the only thing the client will see is "you are having conversation with state"
  //loads form and html, but its not the same as the response header
  //this also adds the port to action attribute to the so the next request goes back to the current port OR the new one that we've migrated to

  static String sendHTMLheader(int localPort, String NewHost, String inLine) {
    
    StringBuilder htmlString = new StringBuilder();
    
    htmlString.append("<html><head> </head><body>\n");
    htmlString.append("<h2>This is for submission to PORT " + localPort + " on " + NewHost + "</h2>\n");
    htmlString.append("<h3>You sent: "+ inLine + "</h3>");
    htmlString.append("\n<form method=\"GET\" action=\"http://" + NewHost +":" + localPort + "\">\n");
    htmlString.append("Enter text or <i>migrate</i>:");
    htmlString.append("\n<input type=\"text\" name=\"person\" size=\"20\" value=\"YourTextInput\" /> <p>\n");
    
    return htmlString.toString(); //returns the concatenated html stream
  }
  //finishes the dynamically built html; adds the submit button, which "resets the page so we can submit more text on the web page
  static String sendHTMLsubmit() {
    return "<input type=\"submit\" value=\"Submit\"" + "</p>\n</form></body></html>\n";
  }
//sends the dynamically assembled html web page to the client via the printstream
  static void sendHTMLtoStream(String html, PrintStream out) {
    
    out.println("HTTP/1.1 200 OK");
    out.println("Content-Length: " + html.length());
    out.println("Content-Type: text/html");
    out.println("");//lines above create the response header		
    out.println(html);
  }
  
}

public class HostServer {
  //we start listening on port 3001
  public static int NextPort = 3000; //starting port for clients
  
  public static void main(String[] a) throws IOException {
    int q_len = 6;//6 simulatenous connections
    int port = 4242; //main port for serversocket
    Socket sock; //initialized socket used by main server port/main socket
    
    ServerSocket servsock = new ServerSocket(port, q_len); //creates doorbel socket
    System.out.println("Elliott/Reagan DIA Master receiver started at port 4242.");
    System.out.println("Connect from 1 to 3 browsers using \"http:\\\\localhost:4242\"\n");
   //doorbell sockt waits for connections
    while(true) {
      
      NextPort = NextPort + 1;//keeps track of what the next poor will be, so that when the client decides to migrate the next poor will already be determined; also keeps true for 
      //...when multiple clients are pointed to localhost that way if browser 3 decides  to migrate first, it will correctly migrate to 305, rather than than 302, which is already in use
      //open socket for requests
      sock = servsock.accept();
      //prints out startup on console
      System.out.println("Starting AgentListener at port " + NextPort);
      //creates new listener agent that will wait for requests at the port stored in the nextport variable
      new AgentListener(sock, NextPort).start();
    }
    
  }
}