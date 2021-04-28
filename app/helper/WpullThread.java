package helper;

import java.io.File;
import java.lang.Process;
import java.lang.ProcessBuilder;

import actions.Create;
import models.Gatherconf;
import models.Node;
import play.Logger;

/**
 * @author I. Kuss Ein Thread, in dem ein Webcrawl gestartet wird. Der Thread
 *         wartet, bis der Crawl beendet ist. Ist der Crawl mit Fehler beendet,
 *         wird ein neuer Thread aufgerufen, der einen erneuten Crawl-Versuch
 *         macht. Es gibt eine Obergrenze für die Anzahl Crawl-Versuche:
 *         maxNumberAttempts.
 */
public class WpullThread extends Thread {

	private Node node = null;
	private Gatherconf conf = null;
	private File crawlDir = null;
	private File outDir = null;
	private String warcFilename = null;
	private String localpath = null;
	private String executeCommand = null;
	private ProcessBuilder pbCDN = null;
	private ProcessBuilder pb = null;
	private File logFileCDN = null;
	private File logFile = null;
	private int exitState = 0;
	private int CDNGathererExitState = 0;
	/**
	 * Der wievielte Versuch ist es, diesen Crawl zu starten ?
	 */
	int attempt = 1;

	private static int maxNumberAttempts = 10;
	private static final Logger.ALogger WebgatherLogger =
			Logger.of("webgatherer");

	/**
	 * Der Konstruktor für diese Klasse.
	 * 
	 * @param pbCDN Ein Objekt der Klasse ProcessBuilder mit Aufrufinformationen
	 *          für den CDN-Crawl.
	 * @param attempt Der wievielte Versuch es ist, diesen Webschnitt zu sammeln.
	 */
	public WpullThread(ProcessBuilder pbCDN, int attempt) {
		this.pbCDN = pbCDN;
		this.attempt = attempt;
		exitState = 0;
	}

	/**
	 * Die Set-Methode für den Parameter node
	 * 
	 * @param node Der Knoten der Website, für die ein neuer Webschnitt gesammelt
	 *          werden soll.
	 */
	public void setNode(Node node) {
		this.node = node;
	}

	/**
	 * Die Set-Methode für den Parameter conf
	 * 
	 * @param conf Die Gatherconf der Website, die gecrawlt werden soll.
	 */
	public void setConf(Gatherconf conf) {
		this.conf = conf;
	}

	/**
	 * Die Methode, um den Parameter crawlDir zu setzen.
	 * 
	 * @param crawlDir Das Verzeichnis (absoluter Pfad), in das wpull seine
	 *          Ergebnisdateien (z.B. WARC-Datei) schreibt.
	 */
	public void setCrawlDir(File crawlDir) {
		this.crawlDir = crawlDir;
	}

	/**
	 * Die Methode, um den Parameter outDir zu setzen.
	 * 
	 * @param outDir Das Verzeichnis (absoluter Pfad), in dem das Endergebnis,
	 *          also der fertig gecrawlte Webschnitt, liegt. Die Crawl-Datei wird
	 *          ggfs. erst nach Ende eines erfolgrecihen Crawls in dieses
	 *          Verzeichnis herein geschoben (bei wpull-Parameter warc-move).
	 */
	public void setOutDir(File outDir) {
		this.outDir = outDir;
	}

	/**
	 * Die Methode, um den Parameter warcFilename zu setzen.
	 * 
	 * @param warcFilename Der Dateiname (kein Pfad, auch keine Endung !) für die
	 *          WARC-Datei.
	 */
	public void setWarcFilename(String warcFilename) {
		this.warcFilename = warcFilename;
	}

	/**
	 * Die Methode, um den Parameter localpath zu setzen.
	 * 
	 * @param localpath Eine URI, unter der die Archivdatei lokal gepeichert ist.
	 *          Fedora benötigt diesesn Parametern, um ein "gemanagtes" Objekt
	 *          anlegen zu können.
	 */
	public void setLocalPath(String localpath) {
		this.localpath = localpath;
	}

	/**
	 * Die Methode, um das Aufrufkommando (wpull) für den Hauptcrawl zu setzen
	 * 
	 * @param executeCommand das Aufrufkommando für den Hauptcrawl
	 */
	public void setExecuteCommand(String executeCommand) {
		this.executeCommand = executeCommand;
	}

	/**
	 * Die Methode, um den Parameter logFileCDN zu setzen.
	 * 
	 * @param logFileCDN Die Logdatei für den CDN-Crawl; Objekttyp 'File'
	 */
	public void setLogFileCDN(File logFileCDN) {
		this.logFileCDN = logFileCDN;
	}

	/**
	 * Die Methode, um exit State auszulesen
	 * 
	 * @return exitState ist der Return-Wert von wpull.
	 */
	public int getExitState() {
		return this.exitState;
	}

	/**
	 * This methods starts a webcrawl and waits for completion.
	 */
	@Override
	public void run() {
		try {
			// 1. Ausführung des CDN-Precrawls
			Process proc = pbCDN.start();
			assert pbCDN.redirectInput() == ProcessBuilder.Redirect.PIPE;
			assert pbCDN.redirectOutput().file() == logFileCDN;
			assert proc.getInputStream().read() == -1;
			CDNGathererExitState = proc.waitFor();
			/**
			 * Exit-Status: 0 = Crawl erfolgreich beendet
			 */
			WebgatherLogger.info("CDN-Crawl für " + conf.getName()
					+ " wurde beendet mit Exit-Status " + CDNGathererExitState);

			// 2. Ausführung des Hauptcrawls (URL)
			String[] execArr = executeCommand.split(" ");
			// unmask spaces in exec command
			for (int i = 0; i < execArr.length; i++) {
				execArr[i] = execArr[i].replaceAll("%20", " ");
			}
			executeCommand = executeCommand.replaceAll("%20", " ");
			WebgatherLogger.info("Executing command " + executeCommand);
			// andere Logdatei für den Hauptcrawl anlegen
			logFile = new File(crawlDir.toString() + "/crawl.log");
			logFile.createNewFile();
			WebgatherLogger.info("Logfile = " + crawlDir.toString() + "/crawl.log");
			pb = new ProcessBuilder(execArr);
			assert crawlDir.isDirectory();
			pb.directory(crawlDir);
			pb.redirectErrorStream(true);
			pb.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile));

			proc = pb.start();
			assert pb.redirectInput() == ProcessBuilder.Redirect.PIPE;
			assert pb.redirectOutput().file() == logFile;
			assert proc.getInputStream().read() == -1;
			exitState = proc.waitFor();
			/**
			 * Exit-Status: 0 = Crawl erfolgreich beendet
			 */
			WebgatherLogger.info("Webcrawl for " + conf.getName()
					+ " exited with exitState " + exitState);
			proc.destroy();
			if (exitState == 0 || exitState == 4 || exitState == 8) {
				new Create().createWebpageVersion(node, conf, outDir, localpath);
				WebgatherLogger
						.info("WebpageVersion für " + conf.getName() + "wurde angelegt.");
			}
      
			// Crawl wird erneut angestoßen
			WebgatherLogger.info("Webcrawl for " + conf.getName()
					+ " wird erneut angestoßen. " + attempt + ". Versuch.");
			pbCDN.directory(crawlDir);
			pbCDN.redirectErrorStream(true);
			WpullThread wpullThread = new WpullThread(pbCDN, attempt);
			wpullThread.setNode(node);
			wpullThread.setConf(conf);
			wpullThread.setCrawlDir(crawlDir);
			wpullThread.setOutDir(outDir);
			wpullThread.setWarcFilename(warcFilename);
			wpullThread.setLocalPath(localpath);
			wpullThread.setExecuteCommand(executeCommand);
			wpullThread.setLogFileCDN(logFileCDN);
			wpullThread.start(); // rekursiver Aufruf
		} catch (Exception e) {
			WebgatherLogger.error(e.toString());
			throw new RuntimeException("wpull crawl not successfully started!", e);
		}
	}

}
