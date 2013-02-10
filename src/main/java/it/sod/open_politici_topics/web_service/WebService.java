/*
 * 
 */
package it.sod.open_politici_topics.web_service;

import it.sod.open_politici_topics.SearchComponent;

import java.util.List;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import org.codehaus.jettison.json.JSONArray;


/**
 * <p>The web service version of the {@link EntityMappingManager} interface. This uses Jersey and set up a REST web service
 * See {@link uk.ac.ebi.fg.myequivalents.webservices.client.EntityMappingWSClientTest} for usage examples.</p>
 * 
 * <p>The web service is backed by a {@link ManagerFactory}, which needs to be configured via Spring, see {@link Resources}.
 * By default {@link DbManagerFactory} is used.</p>
 * 
 * <p>Usually these services are located at /ws/mapping, e.g., 
 * "http://localhost:8080/ws/mapping/get?entityId=service1:acc1". You can build the path by appending the value in 
 * &#064;Path to /mapping.</p> 
 *
 * <dl><dt>date</dt><dd>Sep 11, 2012</dd></dl>
 * @author Marco Brandizi
 *
 */
@Path ( "/open-pol-topics" )
public class WebService
{
	@XmlRootElement ( name = "twitter-accounts" )
	@XmlAccessorType ( XmlAccessType.NONE )
	public static class TwitterAccountsWrapper
	{
		@XmlElement ( name = "account" )
		private List<String> accounts;

		protected TwitterAccountsWrapper () {
		}

		public TwitterAccountsWrapper ( List<String> accounts ) {
			this.accounts = accounts;
		}

		public List<String> getAccounts () {
			return accounts;
		}

		protected void setAccounts ( List<String> accounts ) {
			this.accounts = accounts;
		}
		
	}
	
	@GET
	@Path ( "/get-by-topics/json" )
	@Produces ( MediaType.APPLICATION_JSON )
	public JSONArray getPoliticianTwitters ( @QueryParam ( "q") String topicQueryString )
	{
		List<String> result = new SearchComponent ().findPoliticianTwitterAccount ( topicQueryString );
		return new JSONArray ( result );
	}
	
	@GET
	@Path ( "/get-by-topics/xml" )
	@Produces ( MediaType.APPLICATION_XML )
	public TwitterAccountsWrapper getPoliticianTwittersAsXml ( @QueryParam ( "q") String topicQueryString )
	{
		List<String> result = new SearchComponent ().findPoliticianTwitterAccount ( topicQueryString );
		return new TwitterAccountsWrapper ( result );
	}
	
}
