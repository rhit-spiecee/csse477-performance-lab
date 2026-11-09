package lab;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.PriorityQueue;
import java.util.Comparator;

import secret.Server;
import secret.EvilBusinessLogic;
import secret.Request;

public class Main extends Thread{
	private Request request;
	static class RequestTask {
		Request request;
		int priority;
		
		RequestTask(Request request, int priority) {
			this.request = request;
			this.priority = priority;
		}
	}
	
	public static void main(String[] args) {
		Server server = new Server();
		try {
			server.listen(1234);
		} catch (IOException e) {
			System.err.println("Failed to bind to socket");
			System.exit(1);
		}
		
		PriorityQueue<RequestTask> taskQueue = new PriorityQueue<>(
			Comparator.comparingInt((RequestTask t) -> t.priority).reversed()
		);
		
		Thread queueProcessor = new Thread(() -> {
			while (true) {
				RequestTask task = null;
				synchronized (taskQueue) {
					while (taskQueue.isEmpty()) {
						try {
							taskQueue.wait();
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
					}
					task = taskQueue.poll();
				}
				
				if (task != null) {
					Main obj = new Main();
					obj.request = task.request;
					while (Thread.activeCount() >= 4) {
						System.out.println("Waiting for available thread...");
						try {
							Thread.sleep(100);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
					}
					Thread thread = new Thread(obj);
					thread.start();
				}
			}
		});
		queueProcessor.setDaemon(true);
		queueProcessor.start();
		
		while (true) {
			try {
				Request request = server.acceptConnection();
				int priority = extractPriority(request);
				
				synchronized (taskQueue) {
					taskQueue.offer(new RequestTask(request, priority));
					taskQueue.notifyAll();
				}
			} catch (IOException e) {
				System.err.println("Error accepting request");
				e.printStackTrace();
			}
		}
	}
	
	private static int extractPriority(Request request) {
		if (request.path.equals("/super_evil_endpoint") && request.method.equals("POST")) {
			for (String line : request.body.split("\n")) {
				String[] parts = line.split(": ");
				if (parts.length >= 2 && parts[0].equals("evil_priority")) {
					try {
						return Integer.parseInt(parts[1]);
					} catch (NumberFormatException e) {
						return 0;
					}
				}
			}
		}
		return 0;
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
				String[] parts = line.split(": ", 2);
				if (parts.length >= 2) {
					switch (parts[0]) {
					case "evil_name":
						evilName = parts[1];
						break;
					case "evil_priority":
						try {
							evilPriority = Integer.parseInt(parts[1].trim());
						} catch (NumberFormatException e) {
							evilPriority = -1;
						}
						break;
					default:
						break;
					}
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
