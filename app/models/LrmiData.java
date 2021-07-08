/**
 * Copyright 2012 hbz NRW (http://www.hbz-nrw.de/)
 * @author: I. Kuss
 */
package models;

import java.io.StringWriter;

/**
 * Diese Klasse verarbeitet und modelliert LRMI-Metadaten.
 * 
 * @author Ingolf Kuss
 *
 */
public class LrmiData implements java.io.Serializable {

	private static final long serialVersionUID = 1L;
	String content = "";

	/**
	 * Setzt den Inhalt der LRMI-Daten
	 * 
	 * @param content Der Inhalt im Format LRMI.json
	 */
	public void setContent(String content) {
		this.content = content;
	}

	/**
	 * Diese Methode wandelt LRMI.json nach lobid2.json
	 * 
	 * @return Die Daten im Format lobid2.json
	 */
	public String lobidify2() {
		/* ToDo: Hier muss das Mapping von LRMI.json nach lobid.json hin !!! */
		return content;
	}

}
