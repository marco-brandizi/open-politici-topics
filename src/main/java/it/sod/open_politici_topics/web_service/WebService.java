/*
 * 
 */
package it.sod.open_politici_topics.web_service;

import it.sod.open_politici_topics.ScrapeTopics;
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
 * <p>Web service to search politician twitter accounts based on related topics. This is based on data found at 
 * open-politici (see {@link ScrapeTopics} for details).</p>
 * 
 * <h2>Installation</h2>
 * 
 * <p>Build the .war with 'mvn package', you'll get it in target/</p>
 * 
 * <p>Then you to put such .war somewhere into your Java Application server (eg, Tomcat)</p>
 * 
 * <p>You can change the H2 database that is used as index by changing the main/resources/db.properties and rebuilding. 
 * WARNING: this has to be the same DB used by {@link ScrapeTopics}.</p>
 * 
 * <p>You can also run the web server quick-n-dirty via Maven: mvn jetty:run (not recommended in production).</p>  
 * 
 * <h2>Invocation Examples</h2>
 * 
 * <ul>
 * 	<li><a href = "http://localhost:8080/ws/open-pol-topics/get-by-topics/json?q=lavoro">Example 1</a></li>
 * 	<li><a href = "http://localhost:8080/ws/open-pol-topics/get-by-topics/xml?q=lavoro, sport,istruzione">Example 2</a></li>
 * </ul>
 * 
 * <p>The query string contains topic keywords that are (partially matched) against topic tags. The string is split 
 * by using spaces or commas and the order in which keywords are specified doesn't count (yet...). Moreover, the search
 * is still pretty basic (essentially some SQL LIKE) and we plan more advanced features (eg, TDF/TF, stemming and all that
 * can be provided via Lucene) for the future.</p>
 * 
 * <p>For the moemtn, results are internally scored (by how many times a tag was associated to a politician) and returned in descending 
 * score order.</p>  
 * 
 * <dl><dt>date</dt><dd>Feb 10, 2013</dd></dl>
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
