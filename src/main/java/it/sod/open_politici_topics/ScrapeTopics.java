package it.sod.open_politici_topics;

import static java.lang.System.err;
import static java.lang.System.out;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
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
 * TODO: Comment me!
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
		
		out.print ( "[" );
		String topSep = "";
		
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
				
				out.println ( topSep + "{" );
				out.printf  ( "  \"twitterAccount\" : \"%s\", \n", politicianTwitter );
				out.println ( "  \"topics\" : [" );

				String sep = "";
				for ( String[] topicRec: topics ) 
				{
					out.printf ( "%s    { \"topicLabel\" : \"%s\", \"topicWeight\": %s }", sep, topicRec [ 0 ], topicRec [ 1 ] );
					sep = ",\n";
				}
				out.print ( "}" );
				topSep = ",\n\n";	
			}	
		}
		out.println ( "]\n" );
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
