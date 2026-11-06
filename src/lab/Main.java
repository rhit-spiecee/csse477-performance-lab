package lab;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import secret.Server;
import secret.EvilBusinessLogic;
import secret.Request;

public class Main extends Thread{
	private Request request;
	public static void main(String[] args) {
		Server server = new Server();
		try {
			server.listen(1234);
		} catch (IOException e) {
			System.err.println("Failed to bind to socket");
			System.exit(1);
		}
		while (true) {
			try {
				Request request = server.acceptConnection();
				Main obj = new Main();
				obj.request = request;
				Thread thread = new Thread(obj);
				thread.start();
			} catch (IOException e) {
				System.err.println("Error accepting request");
				e.printStackTrace();
			}
		}
	}

	public void run(){
		System.out.println("Handling request for " + request.path);
		switch (request.path) {
		case "/":
			request.respondFile(200, "root/index.html");
			break;
		case "/super_evil_endpoint":
			if (!request.method.equals("POST")) {
				request.respondFile(404, "root/404.html");
				break;
			}
			String evilName = "";
			int evilPriority = -1;
			for (String line : request.body.split("\n")) {
				// very crude arg parsing but it will work for this
				String[] parts = line.split(": ");
				switch (parts[0]) {
				case "evil_name":
					evilName = parts[1];
					break;
				case "evil_priority":
					evilPriority = Integer.parseInt(parts[1]);
					break;
				default:
					break;
				}
			}
			if (evilName.isEmpty() || evilPriority < 0) {
				request.respond(400, "text/plain", "bad request");
				return;
			}
			
			// instantiate the evil logic
			EvilBusinessLogic evilLogic = new EvilBusinessLogic(evilName, evilPriority);
			// process the evil data
			while (!evilLogic.isDone()) evilLogic.crunchData();
			// get the evil result
			String result = evilLogic.getResult();
			// respond with the evil result
			request.respond(200, "text/plain", result);

			break;
		default:
			if (Files.exists(Path.of("root" + request.path))) {
				request.respondFile(200, "root" + request.path);
			} else {
				request.respondFile(404, "root/404.html");
			}
		}
	}
}
