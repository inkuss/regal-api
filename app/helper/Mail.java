package helper;

import java.util.*;
import javax.mail.*;
import javax.mail.internet.*;
import java.net.InetAddress;
import java.net.UnknownHostException;
import play.Logger;
import play.Play;

/**
 * @author I. Kuss
 * @description Class for sending an E-Mail with javax.mail
 *
 */
public class Mail {

	private String to;
	private String from;
	private String message;
	private String subject;
	private String smtpServ;
	private String host;

	/**
	 * Konstruktor für die Klasse Mail. Legt SMTP-Server und aktuellen Hostnamen
	 * fest
	 */
	public Mail() {
		this.smtpServ = Play.application().configuration().getString("javax.mail.smtpServ");
		try {
			this.host = InetAddress.getLocalHost().getHostName();
		} catch (UnknownHostException e) {
			play.Logger.warn("Hostname nicht bekannt: {}", e);
			play.Logger.info("Nehme als Default-Hostnamen \"localhost\"");
			this.host = "localhost";

		}
	}

	/**
	 * verschickt eine Email mit javax.mail. Absender, Empfänger, Betreffzeile
	 * und die eigentliche Nachricht (String) müssen vorher als Instanzvariablen
	 * gesetzt worden sein.
	 * 
	 * @return int (0 = OK, -1 = ERROR)
	 */
	public int sendMail() {
		try {

			Properties props = System.getProperties();
			props.put("mail.transport.protocol", "smtp");
			props.put("mail.smtp.starttls.enable", "true");
			props.put("mail.smtp.host", smtpServ);
			props.put("mail.smtp.auth", "true");
			Authenticator auth = new SMTPAuthenticator();
			Session session = Session.getInstance(props, auth);
			// -- Create a new message --
			Message msg = new MimeMessage(session);
			// -- Set the FROM and TO fields --
			msg.setFrom(new InternetAddress(from + "@" + host));
			msg.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to, false));
			msg.setSubject(subject);
			msg.setText(message);
			// -- Set some other header information --
			// msg.setHeader("Content-Type", "text/pain");
			msg.setSentDate(new Date());
			// -- Send the message --
			Transport.send(msg);
			play.Logger.info("Message sent to" + to + " OK.");
			return 0;
		} catch (Exception ex) {
			ex.printStackTrace();
			play.Logger.error("Exception: " + ex);
			return -1;
		}
	}

	private class SMTPAuthenticator extends javax.mail.Authenticator {
		@Override
		public PasswordAuthentication getPasswordAuthentication() {
			// (sender's email id)
			String username = Play.application().configuration().getString("javax.mail.user");
			String password = Play.application().configuration().getString("javax.mail.password");
			return new PasswordAuthentication(username, password);
		}
	}

	public String getTo() {
		return to;
	}

	public void setTo(String to) {
		this.to = to;
	}

	public String getFrom() {
		return from;
	}

	public void setFrom(String from) {
		this.from = from;
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}

	public String getSubject() {
		return subject;
	}

	public void setSubject(String subject) {
		this.subject = subject;
	}

	public String getSmtpServ() {
		return smtpServ;
	}

}
