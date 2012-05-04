package fi.mikuz.boarder.connection;

import org.json.JSONException;

/**
 * 
 * @author Jan Mikael Lindl�f
 */
public interface ConnectionListener {
	
	public void onConnectionSuccessful(ConnectionSuccessfulResponse connectionSuccessfulResponse) throws JSONException;
	public void onConnectionError(ConnectionErrorResponse connectionErrorResponse);
}
