package it.sod.open_politici_topics;

import static java.lang.System.err;
import static java.lang.System.out;

import it.sod.open_politici_topics.web_service.WebService;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;

import org.apache.commons.lang.StringUtils;
import org.apache.xpath.XPathAPI;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.traversal.NodeIterator;
import org.xml.sax.SAXException;

import uk.ac.ebi.utils.io.IOUtils;
import uk.ac.ebi.utils.regex.RegEx;

/**
 *
 * <p>Builds a search database, containings weighted associations between political topics and politician's twitter accounts.
 * It scrapes <a href = 'http://politici.openpolis.it//argument/tagsVisualization/period/all'>this URL from open-politici</a>, 
 * where topic tags are linked to politician declarations and these in turn to politician profiles.</p>
 *  
 * <h3>How to invoke</h3>
 * 
 * <p>Once you have a .war inside a server, use: 
 * 
 * <pre>
 *   java -jar path/to/the/war it.sod.open_politici_topics.ScrapeTopics [topics-url]
 * </pre></p>
 * 
 * <p>topics-url is http://politici.openpolis.it//argument/tagsVisualization/period/all by default, it could be
 * http://politici.openpolis.it//argument/tagsVisualization/period/week or month for testing purposes.</p>
 * 
 * <p>This command-line update procedure can work in parallel with the web application and hence searches via
 * {@link WebService web service} are still possible while this update is going on, although users will see partial data during this
 * stage (the databse is zeroed).</p>
 * 
 * <dl>
 * <dt>date</dt>
 * <dd>Jan 19, 2013</dd>
 * </dl>
 * 
 * @author Marco Brandizi
 * 
 */
public class ScrapeTopics
{
	public static void main ( String[] args ) throws Exception
	{		
		String urlStr = args.length > 0 ? args[0] : "http://politici.openpolis.it//argument/tagsVisualization/period/all";
		err.printf ( "Starting from '%s'\n", urlStr );
		
		// Better to open the DB ASAP, errors can be reported early, without wasting time 
		Connection conn = DbUtils.resetDb ();

		URL url = new URL ( urlStr );
		String topicsHTML = IOUtils.readInputFully ( new InputStreamReader ( url.openStream () ) );
		
		// Fix syntax errors that are known to occur in this file
		topicsHTML = topicsHTML.replace ( "/ >", "/>" );
		topicsHTML = topicsHTML.replace (  "\\\"surname\\\"", "'surname'" );
		topicsHTML = topicsHTML.replace (  "&poors", "&amp;poors" );
		topicsHTML = topicsHTML.replace (  "& Poor", "&amp; Poor" );
		topicsHTML = topicsHTML.replaceAll (  "Tom *\\& *Jerry", "Tom and Jerry" );
		
		RegEx 
			tagLabelRE = new RegEx ( "(.+?)\\(([0-9]+?)\\)" ), 
			tagBlockRE = new RegEx ( 
				".*(<div *?class *?= *\"nuvolaargomenti\" *?>.+?</div>).*", 
				Pattern.DOTALL | Pattern.CASE_INSENSITIVE 
			);
		
		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance ();
		DocumentBuilder db = dbf.newDocumentBuilder ();		
		Document doc = db.parse ( new ByteArrayInputStream ( topicsHTML.getBytes () ) );
		NodeIterator tagItr = XPathAPI.selectNodeIterator ( doc, "//div[ @class = 'nuvolaargomenti' ]/span/a" );
		
		Set<String> declarationURLs = new HashSet<String> (), politicianURLs = new HashSet<String> ();
		
		PreparedStatement insertStmt = conn.prepareStatement ( "INSERT INTO topics ( twitter, topic, weight ) VALUES (?, ?, ?)" );
				
		for ( Node tagNode = tagItr.nextNode (); tagNode != null; tagNode = tagItr.nextNode () )
		{
			// /argomento/4335
			String tagUrl = StringUtils.trimToNull ( 
				( (Attr) tagNode.getAttributes ().getNamedItem ( "href" ) ).getValue () );
			if ( tagUrl == null ) continue;
			
			String tagLabel = StringUtils.trimToNull ( tagNode.getTextContent () );
			if ( tagLabel == null ) continue;

			if ( ! ( tagUrl.startsWith ( "/argomento/" ) && tagUrl.length () > "/argomento/".length () ) )
				continue;
			
			String tagId = tagUrl.substring ( "/argomento/".length () );
			
			String tagWeight = "1";
			String labelChunks[] = tagLabelRE.groups ( tagLabel );
			if ( labelChunks != null && labelChunks.length >= 3) {
				tagLabel = labelChunks [ 1 ];
				tagWeight = labelChunks [ 2 ];
			}

			err.printf ( "Processing topic: '%s' (#%s)\n", tagLabel, tagId );
			
			// out.printf ( "%s\t%s\t%s\n", tagId, tagLabel, tagWeight );
			for ( String declUrl: getDeclarationUrls ( tagId ) )
			{
				if ( declarationURLs.contains ( declUrl ) ) continue;
				declarationURLs.add ( declUrl );
				
				err.printf ( "Processing declaration: '%s'\n", declUrl );

				String politicianURL = getPoliticianURL ( declUrl );
				if ( politicianURL == null ) continue;
				if ( politicianURLs.contains ( politicianURL ) ) continue;
				politicianURLs.add ( politicianURL );
				
				err.printf ( "Processing Politician URL: '%s'\n", politicianURL );

				String politicianHTML = null;
				try {
					politicianHTML = IOUtils.readInputFully ( new InputStreamReader ( new URL ( politicianURL ).openStream () ) );
				} 
				catch ( IOException ex )
				{
					err.printf ( "Error while downloading from '%s', skipping\n", politicianURL );
					ex.printStackTrace ( err );
					continue;
				}
				if ( politicianHTML == null ) continue; 
				
				String politicianTwitter = getPoliticianTwitterAccount ( politicianHTML );
				if ( politicianTwitter == null ) continue;
				
				err.printf ( "Twitter account found: '%s'\n", politicianTwitter );

				// Now focus on the topics block only, this makes it faster and avoids several HTML errors these files have.
				String politicianHTMLChunks[] = tagBlockRE.groups ( politicianHTML );
				if ( politicianHTMLChunks == null || politicianHTMLChunks.length < 2) continue;
				
				politicianHTML = politicianHTMLChunks [ 1 ];
								
				DocumentBuilderFactory dbf1 = DocumentBuilderFactory.newInstance ();
				dbf1.setSchema ( null );
				Document politicianDOM = null;
				try
				{
					DocumentBuilder db1 = dbf1.newDocumentBuilder ();		
					politicianDOM = db1.parse ( new ByteArrayInputStream ( politicianHTML.getBytes () ) );
				} 
				catch ( ParserConfigurationException | SAXException | IOException ex ) 
				{
					err.printf ( "Internal error while parsing HTML from '%s', skipping\n", politicianURL );
					ex.printStackTrace ( err );
					continue;
				}
				if ( politicianDOM == null ) continue; 
				
				List<String[]> topics = getPoliticianTopics ( politicianDOM );
				if ( topics == null || topics.size () == 0 ) continue;
				
				for ( String[] topicRec: topics ) 
				{
					insertStmt.setString ( 1, politicianTwitter );
					insertStmt.setString ( 2, topicRec [ 0 ] );
					insertStmt.setInt ( 3, Integer.parseInt ( StringUtils.abbreviate ( topicRec [ 1 ], 255 ) ) );
					insertStmt.addBatch ();
					err.println ( "Added: " + insertStmt );
				}
				insertStmt.executeBatch ();
				conn.commit ();
			}	
		}
		conn.close ();
	}
	
	
	private static Set<String> getDeclarationUrls ( String tagId )
	{
		Set<String> result = new HashSet<String> ();
		
	  // All the declarations about a topic
		String topicRSSLink = "http://politici.openpolis.it/feed/tagDeclarations/" + tagId; 
		err.printf ( "Topic's URL is: '%s'\n", topicRSSLink );

		String topicRSSHTML = null;
		try {
			topicRSSHTML = IOUtils.readInputFully ( new InputStreamReader ( new URL ( topicRSSLink ).openStream () ) );
		} 
		catch ( IOException ex )
		{
			err.printf ( "Error while declaration URLs for topic #%s, returning null\n", tagId );
			ex.printStackTrace ( err );
			return result;
		}
		if ( topicRSSHTML == null ) return result; 
		
		topicRSSHTML = topicRSSHTML.replace ( "\u000c", "" );
		
		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance ();
		NodeIterator itemItr = null;
		try
		{
			DocumentBuilder db = dbf.newDocumentBuilder ();		
			Document doc = db.parse ( new ByteArrayInputStream ( topicRSSHTML.getBytes () ) );
			itemItr = XPathAPI.selectNodeIterator ( doc, "//entry/link" );
		} 
		catch ( ParserConfigurationException | SAXException | IOException | TransformerException ex )
		{
			err.printf ( "Error while declaration URLs for topic #%s, returning null\n", tagId );
			ex.printStackTrace ( err );
			return result;
		}
		
		if ( itemItr == null ) return result;
		for ( Node itemNode = itemItr.nextNode (); itemNode != null; itemNode = itemItr.nextNode () )
		{
			NamedNodeMap attrs = itemNode.getAttributes ();
			if ( attrs == null ) continue;
			
			Attr attr = (Attr) attrs.getNamedItem ( "href" );
			if ( attr == null ) continue;
			
			String itemUrl = StringUtils.trimToNull ( attr.getValue () ); 
			result.add ( itemUrl );
		}
		return result;
	}
	
	private static String getPoliticianURL ( String declarationURL ) 
	{
		try
		{
			String declarationHTML = IOUtils.readInputFully ( new InputStreamReader ( new URL ( 
				declarationURL ).openStream () ));
			
			if ( declarationURL == null ) return null;
			int urlMarker = declarationHTML.indexOf ( "\"/politico/" );
			if ( urlMarker == -1 ) return null;

			int urlEndMarker = declarationHTML.indexOf ( '"', ++urlMarker );
			if ( urlEndMarker == -1 ) return null;
			
			return "http://politici.openpolis.it/" + declarationHTML.substring ( urlMarker, urlEndMarker );
		} 
		catch ( IOException  ex )
		{
			err.printf ( "Error while getting URL for the declaration '%s', returning null\n" );
			ex.printStackTrace ( err );
			return null;
		}
	}


	private static String getPoliticianTwitterAccount ( String politicianHTML )
	{ 
		// No, it's not this that we want
		politicianHTML = politicianHTML.replaceAll ( "http?://twitter.com/openpolis", "" );
		
		// eg, https://twitter.com/FraMirabelli, https://twitter.com/#!/pbersani
		String twitterChunks[] = new RegEx ( 	
			".*(\"|')https?://twitter\\.com/(#!/)?(.+?)(\"|').*", Pattern.DOTALL ).groups ( politicianHTML );
		return ( twitterChunks == null || twitterChunks.length < 5 ) ? null : twitterChunks [ 3 ];
	}

	private static List<String[]> getPoliticianTopics ( Document politicianDOM )
	{
		List<String[]> result = new LinkedList<String[]> ();
		RegEx tagLabelPattern = new RegEx ( "(.+?)\\(([0-9]+?)\\)" );
		
		NodeIterator topicItr;
		try {
			topicItr = XPathAPI.selectNodeIterator ( politicianDOM, "//div[@class = 'nuvolaargomenti']/span/a" );
		} 
		catch ( TransformerException ex )
		{
			err.printf ( "Error while parsing the politician HTML (XPath against topics), returning null\n" );
			ex.printStackTrace ( err );
			return result;
		}
		
		for ( Node topicNode = topicItr.nextNode (); topicNode != null; topicNode = topicItr.nextNode () )
		{
			String tagLabel = StringUtils.trimToNull ( topicNode.getTextContent () );
			if ( tagLabel == null ) continue;
			
			String tagWeight = "1";
			String labelChunks[] = tagLabelPattern.groups ( tagLabel );
			if ( labelChunks != null && labelChunks.length >= 3) {
				tagLabel = labelChunks [ 1 ];
				tagWeight = labelChunks [ 2 ];
			}
			result.add ( new String [] { tagLabel, tagWeight } );
		}
		return result;
	}
	
}
