/*
 * 
 */
package it.sod.open_politici_topics;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import org.apache.commons.lang.StringUtils;

/**
 * TODO: Comment me!
 *
 * <dl><dt>date</dt><dd>Feb 9, 2013</dd></dl>
 * @author Marco Brandizi
 *
 */
public class SearchComponent
{
	public List<String> findPoliticianTwitterAccount ( String topicQueryString )
	{
		topicQueryString = StringUtils.trimToNull ( topicQueryString );
		if ( topicQueryString == null ) return new LinkedList<> ();
		
		return findPoliticianTwitterAccountByTopics ( topicQueryString.split ( "(,|, | )" ) );
	}

	public List<String> findPoliticianTwitterAccountByTopics ( String... topics )
	{
		Map<String, Integer> accounts = new HashMap<String, Integer>();
		
		try
		{
			Connection conn = DbUtils.createConnection ();
			PreparedStatement qstmt = conn.prepareStatement ( 
				"SELECT twitter, sum (weight) as tot_weight FROM TOPICS " +
				"WHERE LOWER(topic) LIKE ? GROUP BY twitter ORDER BY tot_weight DESC;" 
			);
			
			for ( String topic: topics ) 
			{
				topic = StringUtils.trimToNull ( topic );
				if ( topic == null ) continue; 
				
				qstmt.setString ( 1, "%" + topic.toLowerCase () + "%" );
				for ( ResultSet rs = qstmt.executeQuery (); rs.next (); )
				{
					String twitter = rs.getString ( "twitter" );
					int score = rs.getInt ( "tot_weight" );
					
					Integer totScore = accounts.get ( twitter );
					if ( totScore == null ) 
						accounts.put ( twitter, score );
					else
						accounts.put ( twitter, totScore + score );
				}
			}

			// Now sort based on the score
			//
			SortedSet<Map.Entry<String, Integer>> accountsIndex = new TreeSet<Map.Entry<String, Integer>> (
				new Comparator<Map.Entry<String,Integer>>() {
					@Override	public int compare ( Entry<String, Integer> e1, Entry<String, Integer> e2 ) {
						return e2.getValue ().compareTo ( e1.getValue () );
					}
			});
			accountsIndex.addAll ( accounts.entrySet () );

			List<String> result = new LinkedList<String> ();
			for ( Map.Entry<String, Integer> entry: accountsIndex )
				result.add ( entry.getKey () );

			return result;
		} 
		catch ( SQLException ex ) {
			throw new RuntimeException ( "Error while connecting to the politician database: " + ex.getMessage (), ex );
		}
	}
}
